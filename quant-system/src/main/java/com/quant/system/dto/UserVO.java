package com.quant.system.dto;

import java.time.LocalDateTime;
import java.util.List;

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

    /** 角色：ADMIN=管理员 GUEST=临时账号（V5.58） */
    private String role;

    /** 是否管理员（前端渲染判定用） */
    private boolean admin;

    /** 权限码清单（管理员=全量；临时账号=所分配的码；V5.58） */
    private List<String> permissions;
}
