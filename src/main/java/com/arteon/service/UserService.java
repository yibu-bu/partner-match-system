package com.arteon.service;

import com.arteon.domain.User;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

public interface UserService extends IService<User> {

    /**
     * 用户注册
     *
     * @param userAccount   用户账户
     * @param userPassword  用户密码
     * @param checkPassword 校验密码
     * @param planetCode    星球编号
     * @return 新用户的id
     */
    long userRegister(String userAccount, String userPassword, String checkPassword, String planetCode);

    /**
     * 用户登录
     *
     * @param userAccount  用户账户
     * @param userPassword 用户密码
     * @param request      HTTP请求的请求体
     * @return 脱敏后的用户信息
     */
    User userLogin(String userAccount, String userPassword, HttpServletRequest request);

    /**
     * 用户脱敏
     *
     * @param originUser 原用户
     * @return 脱敏用户
     */
    User getSafetyUser(User originUser);

    /**
     * 用户注销
     *
     * @param request HTTP请求的请求体
     * @return 操作结果
     */
    int userLogout(HttpServletRequest request);

    /**
     * 通过标签列表查询用户（要求用户包含列表中所有的标签，使用sql实现）
     *
     * @param tagNameList 标签名列表
     * @return List Of User
     */
    @Deprecated
    // 标记为过时，不建议使用
    List<User> searchUsersByTagsBySql(List<String> tagNameList);

    /**
     * 通过标签列表查询用户（要求用户包含列表中所有的标签，使用Java代码实现）
     *
     * @param tagNameList 标签名列表
     * @return List Of User
     */
    List<User> searchUsersByTags(List<String> tagNameList);

    User getLoginUser(HttpServletRequest request);

    int updateUser(User user, User loginUser);

    boolean isAdmin(User loginUser);

    /**
     * 是否为管理员
     *
     * @param request HTTP请求
     * @return true-是管理员，false-不是管理员或者未登录
     */
    boolean isAdmin(HttpServletRequest request);

    Page<User> recommendUsers(long pageNum, long pageSize, User loginUser);

    /**
     * 使用编辑距离算法计算两个用户标签的相似度，为当前用户推荐相似用户
     *
     * @param num  返回的用户数量
     * @param user 当前用户
     * @return List of User
     */
    List<User> matchUsers(long num, User user);

}
