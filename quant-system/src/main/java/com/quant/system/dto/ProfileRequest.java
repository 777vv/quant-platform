package com.quant.system.dto;

import lombok.Data;

/**
 * 用户资料维护请求
 */
@Data
public class ProfileRequest {

    /** 昵称（界面展示） */
    private String nickname;

    /** 通知收件邮箱（信号汇总邮件收件人） */
    private String email;
}
