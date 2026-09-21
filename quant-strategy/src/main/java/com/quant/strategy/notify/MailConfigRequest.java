package com.quant.strategy.notify;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 邮件通知配置保存请求（V4.9，平台配置页邮件卡）。
 *
 * <p>password 语义与 AI 模型配置的 Token 一致：**留空或回传打码值表示不修改已存授权码**。
 *
 * @param enabled   邮件通知总开关（null = 保持原值）
 * @param host      SMTP 服务器（如 smtp.qq.com；空 = 未启用邮件）
 * @param port      SMTP 端口（SSL 465 / STARTTLS 587；null = 保持原值）
 * @param username  SMTP 登录账号（通常即发件邮箱）
 * @param password  SMTP 授权码（留空/打码 = 保持原值）
 * @param fromAddr  发件人地址（空 = 用 SMTP 账号）
 * @param toAddr    收件人地址（空 = 取用户资料的通知邮箱）
 */
public record MailConfigRequest(
        /** 总开关（null=保持原值） */
        Boolean enabled,
        /** SMTP 服务器 */
        @Size(max = 128, message = "SMTP 服务器地址过长") String host,
        /** SMTP 端口 */
        @Min(value = 1, message = "SMTP 端口需在 1~65535 之间")
        @Max(value = 65535, message = "SMTP 端口需在 1~65535 之间") Integer port,
        /** SMTP 登录账号 */
        @Size(max = 128, message = "SMTP 账号过长") String username,
        /** SMTP 授权码（留空=保持原值） */
        @Size(max = 128, message = "授权码过长") String password,
        /** 发件人地址 */
        @Size(max = 128, message = "发件人地址过长") String fromAddr,
        /** 收件人地址 */
        @Size(max = 128, message = "收件人地址过长") String toAddr) {
}
