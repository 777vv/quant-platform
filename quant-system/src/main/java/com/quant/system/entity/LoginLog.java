package com.quant.system.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 登录日志（V5.46）：每次登录尝试记一行（成功与失败都记），用于安全审计与异常排查。
 */
@TableName("login_log")
public class LoginLog {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录用户名（失败时也记：便于发现撞库尝试或自己输错用户名） */
    private String username;

    /** 1=登录成功 0=登录失败 */
    private Integer success;

    /** 失败原因（成功时为 null；文案与前端提示一致，如"用户名或密码错误"、"账号已锁定"） */
    private String failReason;

    /** 客户端 IP（已按反向代理链取真实来源，见 ClientIpUtils） */
    private String ip;

    /** IP 归属地（内网/本机直接标注；公网地址的手机号段/市一级解析见文档口径） */
    private String ipLocation;

    /** 客户端 UA（截断保存） */
    private String userAgent;

    /** traceId：可与该次请求在日志文件里的全部记录串联 */
    private String traceId;

    /** 登录时间（库默认值） */
    private LocalDateTime createdAt;

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

    public Integer getSuccess() {
        return success;
    }

    public void setSuccess(Integer success) {
        this.success = success;
    }

    public String getFailReason() {
        return failReason;
    }

    public void setFailReason(String failReason) {
        this.failReason = failReason;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getIpLocation() {
        return ipLocation;
    }

    public void setIpLocation(String ipLocation) {
        this.ipLocation = ipLocation;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
