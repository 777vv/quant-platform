package com.quant.system.dto;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 用户信息视图
 */
@Data
public class UserVO {

    /** 用户ID */
    private Long id;

    /** 用户名（登录名） */
    private String username;

    /** 昵称 */
    private String nickname;

    /** 通知收件邮箱 */
    private String email;

    /** 最后登录时间 */
    private LocalDateTime lastLoginAt;
}
