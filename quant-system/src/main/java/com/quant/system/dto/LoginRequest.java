package com.quant.system.dto;

import jakarta.validation.constraints.NotBlank;

import lombok.Data;

/**
 * 登录请求
 */
@Data
public class LoginRequest {

    /** 用户名 */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 明文密码（经 HTTPS 传输，服务端 BCrypt 校验） */
    @NotBlank(message = "密码不能为空")
    private String password;
}
