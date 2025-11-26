package com.arteon.job;

import com.arteon.domain.User;
import com.arteon.service.UserService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 缓存预热
 */
@Component
@Slf4j
public class PreCacheJob {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private UserService userService;

    @Resource
    private RedissonClient redissonClient;

    // 为这些重点用户预热缓存
    private List<Long> mainUserIdList = Arrays.asList(1L);

    @Scheduled(cron = "*/5 * * * * ?")  // 每五秒触发一次（实际上线项目不需要这么频繁，这里为了看到效果）
    public void cacheRecommendUsers() {
        // 获取锁
        RLock lock = redissonClient.getLock("pm:precachejob:docache:lock");// redis key推荐命名方法
        // 上锁
        try {
            if (lock.tryLock(0, -1, TimeUnit.SECONDS)) {  // 上锁一定要设置有效期，设为-1默认是30秒
                // Thread.sleep(30000);  // 模拟方法执行时间很长，看看Redisson的看门狗机制
                for (Long userId : mainUserIdList) {
                    // 从数据库中查数据、
                    Page<User> userPage = userService.page(new Page<>(1, 20));  // 暂时写第1页20条数据
                    String redisKey = String.format("pm:user:recommend:%s", userId);
                    try {
                        redisTemplate.opsForValue().set(redisKey, userPage, 60, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        log.error("redis set key error", e);
                    }
                }
            }
        } catch (InterruptedException e) {
            log.error("cacheRecommendUsers error", e);
        } finally {
            // 解锁一定要写在finally块中，否则一旦出现异常就会导致死锁
            if (lock.isHeldByCurrentThread()) {  // Redisson提供了现成的方法判断锁是不是自己的，底层是通过线程id区分
                lock.unlock();
            }
        }

    }

}
