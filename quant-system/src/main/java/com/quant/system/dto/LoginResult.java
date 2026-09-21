package com.quant.system.dto;

import lombok.Data;

/**
 * 登录结果：token + 用户信息
 */
@Data
public class LoginResult {

    /** SaToken 签发的会话令牌（前端后续请求经 satoken 头携带） */
    private String token;

    /** 用户信息 */
    private UserVO user;
}
