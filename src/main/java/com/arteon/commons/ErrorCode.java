package com.arteon.commons;

import lombok.Getter;

/**
 * 自定义异常枚举
 */
@Getter
public enum ErrorCode {
    SUCCESS(0, "ok", ""), // 这个严格来说并不需要，因为这个类是异常枚举，先放这吧
    PARAMS_ERROR(40000, "请求参数错误", ""),
    NULL_ERROR(40001, "请求数据为空", ""),
    NOT_LOGIN(40100, "未登录", ""),
    NO_AUTH(40101, "无权限", ""),
    SYSTEM_ERROR(50000, "系统内部异常", "");

    private final Integer code;

    private final String message;

    private final String description;

    ErrorCode(Integer code, String message, String description) {
        this.code = code;
        this.message = message;
        this.description = description;
    }

}
