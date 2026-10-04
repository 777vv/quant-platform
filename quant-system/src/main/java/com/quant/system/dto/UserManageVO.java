package com.quant.system.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

/**
 * 用户管理列表行（V5.58，仅管理员可见；不含密码哈希）
 */
@Data
public class UserManageVO {

    /** 用户ID */
    private Long id;

    /** 用户名（登录名） */
    private String username;

    /** 昵称 */
    private String nickname;

    /** 角色：ADMIN / GUEST */
    private String role;

    /** 权限码清单 */
    private List<String> permissions;

    /** 是否启用：1=启用 0=停用 */
    private Integer enabled;

    /** 过期时间（null=永久） */
    private LocalDateTime expiresAt;

    /** 备注（给谁用的） */
    private String remark;

    /** 最后成功登录时间 */
    private LocalDateTime lastLoginAt;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
