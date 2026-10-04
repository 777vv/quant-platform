package com.quant.system.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建临时账号请求（V5.58，仅管理员可用）。角色固定 GUEST，不可经此接口创建管理员。
 */
@Data
public class UserCreateRequest {

    /** 用户名（登录名，唯一；字母/数字/下划线） */
    @NotBlank(message = "用户名不能为空")
    @Size(max = 32, message = "用户名不能超过 32 位")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字与下划线")
    private String username;

    /** 昵称（界面展示，可空） */
    @Size(max = 32, message = "昵称不能超过 32 位")
    private String nickname;

    /** 初始密码（6~32 位，BCrypt 落库） */
    @NotBlank(message = "初始密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度需在 6~32 位之间")
    private String password;

    /** 权限码清单（非法码会被过滤） */
    private List<String> permissions;

    /** 有效期至（当日 23:59:59 失效；null=永久） */
    private LocalDate expiresOn;

    /** 备注（给谁用的） */
    @Size(max = 255, message = "备注不能超过 255 字")
    private String remark;
}
