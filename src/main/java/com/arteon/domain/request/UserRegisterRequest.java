package com.arteon.domain.request;

import lombok.Data;

import java.io.Serializable;

/**
 * 封装注册请求参数
 */
@Data
public class UserRegisterRequest implements Serializable {

    private static final long serialVersionUID = 7394007236076971588L;

    private String userAccount;

    private String userPassword;

    private String checkPassword;

    private String planetCode;

}
