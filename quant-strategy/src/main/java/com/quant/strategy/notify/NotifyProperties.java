package com.quant.strategy.notify;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 邮件通知配置（quant.notify.*，M4-04）。
 * SMTP 服务器参数走 spring.mail.*（技术文档 6.9：配置文件维护、不建表）；
 * 本配置只承载通知开关与收件人兜底。
 */
@ConfigurationProperties(prefix = "quant.notify")
public class NotifyProperties {

    /** 邮件通知总开关：false 时每日信号摘要静默跳过（测试邮件仍可手动发送） */
    private boolean enabled = false;

    /** 收件人邮箱：留空时取用户资料中的通知邮箱（sys_user.email） */
    private String to = "";

    /** 发件人地址：留空时使用 SMTP 登录账号（spring.mail.username） */
    private String from = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }
}
