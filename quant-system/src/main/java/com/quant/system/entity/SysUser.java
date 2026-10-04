package com.quant.system.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 用户表（V5.58 起支持临时账号：ADMIN=管理员全权限；GUEST=按 permissions 授权的受限账号）
 */
@TableName("sys_user")
public class SysUser {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户名（登录名，唯一） */
    private String username;

    /** BCrypt 哈希后的密码（明文不落库不落日志） */
    private String password;

    /** 昵称（界面展示） */
    private String nickname;

    /** 通知收件邮箱（信号汇总邮件收件人） */
    private String email;

    /** 角色：ADMIN=管理员(全权限) GUEST=临时账号(按 permissions 授权) */
    private String role;

    /** 权限码，逗号分隔（如 menu:funds,action:sync）；仅 GUEST 生效，ADMIN 恒为全权限 */
    private String permissions;

    /** 是否启用：1=启用 0=停用（停用立即踢下线） */
    private Integer enabled;

    /** 过期时间（null=永久；过期后拒绝登录且在线会话失效） */
    private LocalDateTime expiresAt;

    /** 备注（给谁用的、为什么开） */
    private String remark;

    /** 防爆破锁定截止时间（连续失败 5 次后锁定 10 分钟，null=未锁定） */
    private LocalDateTime lockedUntil;

    /** 连续登录失败次数（成功登录后清零） */
    private Integer failCount;

    /** 最后成功登录时间 */
    private LocalDateTime lastLoginAt;

    /** 创建时间（库默认值） */
    private LocalDateTime createdAt;

    /** 更新时间（库自动维护） */
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getPermissions() {
        return permissions;
    }

    public void setPermissions(String permissions) {
        this.permissions = permissions;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public LocalDateTime getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(LocalDateTime lockedUntil) {
        this.lockedUntil = lockedUntil;
    }

    public Integer getFailCount() {
        return failCount;
    }

    public void setFailCount(Integer failCount) {
        this.failCount = failCount;
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
