package com.arteon.constant;

/**
 * 用户模块相关的常量
 */
public interface UserConstant {

    /**
     * 用户登录态，作为 Redis的 Key
     */
    String USER_LOGIN_STATE = "userLoginState";

    /**
     * 默认权限（普通用户）
     */
    int DEFAULT_ROLE = 0;

    /**
     * 管理员权限
     */
    int ADMIN_ROLE = 1;

}
