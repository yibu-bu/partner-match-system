package com.arteon.service.impl;

import com.arteon.commons.ErrorCode;
import com.arteon.constant.UserConstant;
import com.arteon.domain.User;
import com.arteon.exception.BusinessException;
import com.arteon.mapper.UserMapper;
import com.arteon.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.DigestUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.arteon.constant.UserConstant.ADMIN_ROLE;
import static com.arteon.constant.UserConstant.USER_LOGIN_STATE;

@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Resource
    private UserMapper userMapper;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 盐值，混淆密码
     */
    private static final String SALT = "HelloWorld";

    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword, String planetCode) {
        // 1. 校验参数合理性
        if (StringUtils.isAnyBlank(userAccount, userPassword, checkPassword, planetCode)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号过短");
        }
        if (userPassword.length() < 8 || checkPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码过短");
        }
        if (planetCode.length() > 5) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "星球编号过长");
        }
        // 账户不能包含特殊字符
        String validPattern = "[`~!@#$%^&*()+=|{}':;',\\\\[\\\\].<>/?~！@#￥%……&*（）——+|{}【】‘；：”“’。，、？]";
        Matcher matcher = Pattern.compile(validPattern).matcher(userAccount);
        if (matcher.find()) {
            return -1;
        }
        // 密码和校验密码相同
        if (!userPassword.equals(checkPassword)) {
            return -1;
        }
        // 账户不能重复
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        long count = userMapper.selectCount(queryWrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号重复");
        }
        // 星球编号不能重复
        queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("planetCode", planetCode);
        count = userMapper.selectCount(queryWrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "编号重复");
        }
        // 2. 加密
        String encryptPassword = DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
        // 3. 插入数据
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(encryptPassword);
        user.setPlanetCode(planetCode);
        boolean saveResult = this.save(user);
        if (!saveResult) {
            return -1;
        }
        return user.getId();  // 成功返回新用户id
    }

    @Override
    public User userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        // 1. 校验
        if (StringUtils.isAnyBlank(userAccount, userPassword)) {
            return null;
        }
        if (userAccount.length() < 4) {
            return null;
        }
        if (userPassword.length() < 8) {
            return null;
        }
        // 账户不能包含特殊字符
        String validPattern = "[`~!@#$%^&*()+=|{}':;',\\\\[\\\\].<>/?~！@#￥%……&*（）——+|{}【】‘；：”“’。，、？]";
        Matcher matcher = Pattern.compile(validPattern).matcher(userAccount);
        if (matcher.find()) {
            return null;
        }
        // 2. 加密
        String encryptPassword = DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
        // 查询用户是否存在
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        queryWrapper.eq("userPassword", encryptPassword);
        User user = userMapper.selectOne(queryWrapper);
        // 用户不存在
        if (user == null) {
            log.info("user login failed, userAccount cannot match userPassword");
            return null;
        }
        // 3. 用户脱敏
        User safetyUser = getSafetyUser(user);
        // 4. 记录用户的登录态
        request.getSession().setAttribute(USER_LOGIN_STATE, safetyUser);
        return safetyUser;
    }

    @Override
    public User getSafetyUser(User originUser) {
        if (originUser == null) {
            return null;
        }
        User safetyUser = new User();
        safetyUser.setId(originUser.getId());
        safetyUser.setUsername(originUser.getUsername());
        safetyUser.setUserAccount(originUser.getUserAccount());
        safetyUser.setAvatarUrl(originUser.getAvatarUrl());
        safetyUser.setGender(originUser.getGender());
        safetyUser.setPhone(originUser.getPhone());
        safetyUser.setEmail(originUser.getEmail());
        safetyUser.setPlanetCode(originUser.getPlanetCode());
        safetyUser.setUserRole(originUser.getUserRole());
        safetyUser.setUserStatus(originUser.getUserStatus());
        safetyUser.setCreateTime(originUser.getCreateTime());
        safetyUser.setTags(originUser.getTags());
        return safetyUser;
    }

    @Override
    public int userLogout(HttpServletRequest request) {
        // 移除登录态
        request.getSession().removeAttribute(USER_LOGIN_STATE);
        return 1;  // 暂时固定返回1
    }

    /**
     * 通过标签列表查询用户（要求用户包含列表中所有的标签）
     *
     * @param tagNameList 标签名列表
     * @return List Of User
     */
    @Override
    public List<User> searchUsersByTagsBySql(List<String> tagNameList) {
        // 判断请求参数是否为空
        if (CollectionUtils.isEmpty(tagNameList)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 拼接查询条件
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        for (String tagName : tagNameList) {
            queryWrapper = queryWrapper.like("tags", tagName);
        }
        // 查询
        List<User> userList = userMapper.selectList(queryWrapper);
        // 脱敏并返回
        return userList.stream().map(this::getSafetyUser).collect(Collectors.toList());
    }

    /**
     * 通过标签列表查询用户（要求用户包含列表中所有的标签）（内存查询版）
     *
     * @param tagNameList 标签名列表
     * @return List Of User
     */
    @Override
    public List<User> searchUsersByTags(List<String> tagNameList) {
        // 判断请求参数是否为空
        if (CollectionUtils.isEmpty(tagNameList)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 查询所有用户
        List<User> userList = userMapper.selectList(null);
        // 遍历每一个用户，判断其标签是否符合要求
        Gson gson = new Gson();  // gson序列化对象
        return userList.stream().filter(user -> {
            String tagsJsonStr = user.getTags();
            // 使用gson对象将json串反序列化
            Set<String> tagNameSet = gson.fromJson(tagsJsonStr, new TypeToken<Set<String>>() {
            }.getType());
            // 判断是否为空（避免NPE是程序员的义务……）
            tagNameSet = Optional.ofNullable(tagNameSet).orElse(new HashSet<>()); // Java8新增语法，如果一个对象为空就给它一个默认值
            for (String tagName : tagNameList) {
                if (!tagNameSet.contains(tagName)) {
                    return false;  // 这里返回的boolean类型是stream流过滤是否通过
                }
            }
            return true;
        }).map(this::getSafetyUser).collect(Collectors.toList());
    }

    @Override
    public User getLoginUser(HttpServletRequest request) {
        // 判断参数是否为空
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Object o = request.getSession().getAttribute(USER_LOGIN_STATE);
        if (o == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }
        return (User) o;
    }

    /**
     * 更新用户信息
     *
     * @param user      新信息
     * @param loginUser 当前登录的用户（鉴权用）
     */
    @Override
    public int updateUser(User user, User loginUser) {
        // 判断参数是否为空
        if (user == null || user.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 鉴权（不是管理员且修改的信息不是自己的）
        if (!isAdmin(loginUser) && !Objects.equals(user.getId(), loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH);
        }
        // todo 补充校验，如果用户没有传任何要更新的值，就直接报错，不用执行 update 语句
        // MyBatisPlus默认实现不是updateSelective，没有值会报错
        int i = userMapper.updateById(user);
        if (i == 0) {
            throw new BusinessException(ErrorCode.NULL_ERROR);
        }
        return i;
    }

    @Override
    public boolean isAdmin(User loginUser) {
        if (loginUser == null) {
            return false;
        }
        return loginUser.getUserRole() == UserConstant.ADMIN_ROLE;
    }

    /**
     * 是否为管理员
     *
     * @param request HTTP请求
     * @return true-是管理员，false-不是管理员或者未登录
     */
    @Override
    public boolean isAdmin(HttpServletRequest request) {
        // 仅管理员可查询
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        User user = (User) userObj;
        return user != null && user.getUserRole() == ADMIN_ROLE;
    }

    @Override
    public Page<User> recommendUsers(long pageNum, long pageSize, User loginUser) {
        // 如果redis中有数据，就直接从redis中取数据
        ValueOperations<String, Object> ops = redisTemplate.opsForValue();
        String redisKey = String.format("pm:user:recommend:%s", loginUser.getId());  // 格式化一个redis的key，以冒号分隔是redis常见命名方式
        Page<User> userPage = (Page<User>) ops.get(redisKey);
        // 如果取到了数据就直接返回
        if (userPage != null) {
            return userPage;
        }
        // redis中没有数据，查询数据库并添加到缓存
        // MP实现分页查询非常方便，UserService都有现成的方法，直接传一个Page对象即可
        userPage = this.page(new Page<>(pageNum, pageSize));
        try {
            ops.set(redisKey, userPage, 60, TimeUnit.SECONDS);  // 60秒过期
        } catch (Exception e) {
            log.error("redis set error", e);
        }
        return userPage;
    }

}




