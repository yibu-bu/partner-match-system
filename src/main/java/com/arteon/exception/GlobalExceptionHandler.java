package com.arteon.exception;

import com.arteon.commons.BaseResponse;
import com.arteon.commons.ErrorCode;
import com.arteon.commons.ResultUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 */
@RestControllerAdvice("com.arteon.controller") // ControllerAdvice + ResponseBody
@Slf4j // 使用这个注解的类会多一个 log字段，用于记录日志
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)  // 处理业务异常
    public BaseResponse<Object> businessException(BusinessException e) {
        log.error("businessException: " + e.getMessage(), e);
        return ResultUtils.error(e.getCode(), e.getMessage(), e.getDescription());
    }

    @ExceptionHandler(RuntimeException.class)  // 处理系统 bug
    public BaseResponse<Object> runtimeException(BusinessException e) {
        log.error("runtimeException:" + e.getMessage(), e);
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, e.getMessage(), "");
    }

}
