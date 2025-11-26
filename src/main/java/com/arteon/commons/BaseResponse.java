package com.arteon.commons;

import lombok.Data;

import java.io.Serializable;

@Data
public class BaseResponse<T> implements Serializable {

    private static final long serialVersionUID = -8379848498892031226L;

    /**
     * 状态码
     */
    private Integer code;

    /**
     * 相应信息
     */
    private String message;

    /**
     * 具体描述
     */
    private String description;

    /**
     * 返回的数据
     */
    private T data;

    public BaseResponse(Integer code, String message, T data, String description) {
        this.code = code;
        this.message = message;
        this.description = description;
        this.data = data;
    }

    public BaseResponse(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.description = "";
    }

    public BaseResponse(Integer code, T data) {
        this.code = code;
        this.data = data;
        this.message = "";
        this.description = "";
    }

    public BaseResponse(ErrorCode errorCode) {
        this.code = errorCode.getCode();
        this.message = errorCode.getMessage();
        this.description = errorCode.getDescription();
        this.data = null;
    }

}
