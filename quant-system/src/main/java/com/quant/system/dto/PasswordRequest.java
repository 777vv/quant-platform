package com.quant.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改密码请求
 */
@Data
public class PasswordRequest {

    /** 原密码（服务端 BCrypt 校验，错误则拒绝修改） */
    @NotBlank(message = "原密码不能为空")
    private String oldPassword;

    /** 新密码（6-32 位） */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 32, message = "新密码长度须为 6-32 位")
    private String newPassword;

    /** 确认新密码（须与 newPassword 一致） */
    @NotBlank(message = "确认密码不能为空")
    private String confirmPassword;
}
