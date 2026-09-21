package com.quant.strategy.notify;

/**
 * 邮件通知配置只读视图（FR4 平台配置邮件卡片）：SMTP 参数来自配置文件，界面只展示不可编辑
 */
public record MailConfigVO(
        /** 通知总开关（quant.notify.enabled） */
        boolean enabled,
        /** SMTP 服务器地址（spring.mail.host） */
        String host,
        /** SMTP 端口（spring.mail.port） */
        Integer port,
        /** 发件账号（spring.mail.username，脱敏展示） */
        String username,
        /** 发件人地址（配置 from 或 SMTP 账号） */
        String from,
        /** 实际收件人（配置 to 优先，其次用户资料邮箱） */
        String to,
        /** SMTP 参数是否配置完整（host 非空且邮件 bean 已装配） */
        boolean configured) {
}
