package com.arteon.domain.request;

import lombok.Data;

import java.io.Serializable;

/**
 * 封装登录请求参数
 */
@Data
public class UserLoginRequest implements Serializable {

    private static final long serialVersionUID = -4344043180983812915L;

    private String userAccount;

    private String userPassword;

}
