package com.arteon;

import com.arteon.domain.User;
import com.arteon.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.StopWatch;

import javax.annotation.Resource;
import java.util.ArrayList;

/**
 * 向数据库插入大量测试数据
 */
@SpringBootTest
public class ImportUsersTest {

    @Resource
    private UserService userService;

    @Test
    public void insertUsers() {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start(); // 开始计时
        final int INSERT_NUM = 100000;  // 插入十万条数据
        ArrayList<User> userList = new ArrayList<>();
        for (int i = 0; i < INSERT_NUM; i++) {
            User user = new User();
            user.setUsername("假用户");
            user.setUserAccount("fakeUser");
            user.setGender(0);
            user.setUserPassword("12345678");
            user.setPhone("123");
            user.setEmail("123@qq.com");
            user.setUserStatus(0);
            user.setIsDelete(0);
            user.setUserRole(0);
            user.setPlanetCode("1111111");
            user.setTags("[]");
            userList.add(user);
            // 单次插入
            // userService.save(user);
        }
        // 批量插入
        userService.saveBatch(userList, 10000);  // 批量插入，每一万条为一组
        stopWatch.stop();
        long time = stopWatch.getTotalTimeMillis();
        System.out.println("操作完成时间（ms）：" + time);
        // 单次插入测试结果446688ms，批量插入测试结果14685ms
    }

}
