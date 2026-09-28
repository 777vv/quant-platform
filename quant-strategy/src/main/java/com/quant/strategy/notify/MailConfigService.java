package com.quant.strategy.notify;

import java.util.Properties;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import com.quant.strategy.entity.SysMailConfig;
import com.quant.strategy.mapper.SysMailConfigMapper;

/**
 * 邮件配置服务（V4.9）：SMTP 参数与开关存库（sys_mail_config 单行，平台配置页维护），
 * **发送器运行时按配置构建**——替代 spring.mail.* 启动期自动装配的单例，
 * 保存配置后立即生效，无需重启（与 AI 模型配置同一模式）。
 *
 * <p>配置指纹缓存：配置未变时复用同一个 JavaMailSender（避免每次发信都新建连接池）。
 */
@Service
public class MailConfigService {

    /** 单行配置的固定主键 */
    private static final Long CONFIG_ID = 1L;

    private final SysMailConfigMapper mapper;

    /** 缓存的发送器与其对应配置指纹（指纹变化即重建） */
    private volatile JavaMailSender cachedSender;

    private volatile String cachedFingerprint;

    public MailConfigService(SysMailConfigMapper mapper) {
        this.mapper = mapper;
    }

    /** 当前配置（单行；理论上行由 schema 初始化，缺行时返回带默认值的空壳） */
    public SysMailConfig config() {
        SysMailConfig config = mapper.selectById(CONFIG_ID);
        if (config == null) {
            config = new SysMailConfig();
            config.setId(CONFIG_ID);
            config.setEnabled(0);
            config.setPort(465);
        }
        return config;
    }

    /** SMTP 是否配置完整（host 与账号齐备即可发信；授权码缺失会在发送时报错并透出原因） */
    public boolean isConfigured() {
        SysMailConfig config = config();
        return notBlank(config.getHost()) && notBlank(config.getUsername());
    }

    /**
     * 可用的邮件发送器：按当前库内配置构建/复用；未配置时返回 null（调用方给中文提示）。
     */
    public JavaMailSender sender() {
        SysMailConfig config = config();
        if (!isConfigured()) {
            return null;
        }
        String fingerprint = fingerprint(config);
        JavaMailSender sender = cachedSender;
        if (sender == null || !fingerprint.equals(cachedFingerprint)) {
            synchronized (this) {
                if (cachedSender == null || !fingerprint.equals(cachedFingerprint)) {
                    cachedSender = buildSender(config);
                    cachedFingerprint = fingerprint;
                }
                sender = cachedSender;
            }
        }
        return sender;
    }

    /** 让发送器缓存失效（配置保存后调用，下一次发信即用新配置重建） */
    public void invalidate() {
        cachedSender = null;
        cachedFingerprint = null;
    }

    /**
     * 按给定配置现建一个一次性发送器（不读缓存、不写缓存）。
     * 供"测试发送"使用：测试要按界面当前填写跑（V5.26），不能复用、也不能污染按库内配置缓存的发送器。
     *
     * @param config 生效配置（host/username 必填，port 可空默认 465）
     * @return 可用的发送器
     */
    public JavaMailSender senderFor(SysMailConfig config) {
        return buildSender(config);
    }

    /** 按库内配置构建发送器（SSL 465 / STARTTLS 587 两档） */
    private JavaMailSender buildSender(SysMailConfig config) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(config.getHost().trim());
        sender.setPort(config.getPort() == null ? 465 : config.getPort());
        sender.setUsername(config.getUsername().trim());
        sender.setPassword(config.getPassword() == null ? "" : config.getPassword().trim());
        sender.setDefaultEncoding("UTF-8");
        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        boolean starttls = sender.getPort() == 587;
        props.put("mail.smtp.ssl.enable", Boolean.toString(!starttls));
        props.put("mail.smtp.starttls.enable", Boolean.toString(starttls));
        // 发送超时保护：避免上游 SMTP 挂死占住异步线程池
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "15000");
        props.put("mail.smtp.writetimeout", "15000");
        return sender;
    }

    /** 影响发送器构建的字段拼成指纹（开关与收发件人不影响连接，不参与） */
    private String fingerprint(SysMailConfig config) {
        return config.getHost() + "|" + config.getPort() + "|" + config.getUsername() + "|" + config.getPassword();
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
