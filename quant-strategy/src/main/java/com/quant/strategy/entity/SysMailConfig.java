package com.quant.strategy.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 邮件通知配置（sys_mail_config，单行，V4.9 从 yml 迁入库）。
 *
 * <p>授权码明文存本机库（与 ai_model_config.api_key 同一约定）：接口回显打码、
 * 保存时留空或回传打码值表示不修改。SMTP 参数改动后保存即生效（发送器按配置指纹重建）。
 */
@Data
@TableName("sys_mail_config")
public class SysMailConfig {

    /** 固定主键（单行配置） */
    @TableId(type = IdType.INPUT)
    private Long id;

    /** 邮件通知总开关：1 启用（每日摘要/告警），0 只允许手动测试 */
    private Integer enabled;

    /** SMTP 服务器（如 smtp.qq.com；空 = 未启用邮件） */
    private String host;

    /** SMTP 端口（SSL 465 / STARTTLS 587） */
    private Integer port;

    /** SMTP 登录账号（通常即发件邮箱） */
    private String username;

    /** SMTP 授权码（明文存本机库；接口打码） */
    private String password;

    /** 发件人地址（空 = 用 SMTP 账号） */
    private String fromAddr;

    /** 收件人地址（空 = 取用户资料的通知邮箱） */
    private String toAddr;

    /** 最近修改时间 */
    private LocalDateTime updatedAt;
}
