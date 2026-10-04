package com.quant.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员重置临时账号密码请求（V5.58）。重置后该账号全端下线。
 */
@Data
public class UserPasswordResetRequest {

    /** 新密码（6~32 位） */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度需在 6~32 位之间")
    private String password;
}
