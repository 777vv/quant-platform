package com.quant.strategy.notify;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.quant.common.exception.BizException;
import com.quant.common.spi.MailRecipientProvider;
import com.quant.fund.dto.DashboardOverviewVO;
import com.quant.strategy.core.StrategyRegistry;
import com.quant.strategy.entity.SignalRecord;
import com.quant.strategy.mapper.SignalRecordMapper;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

/**
 * 邮件通知实现（M4-04）：
 * SMTP 参数来自 spring.mail.* 配置（技术文档 6.9），收件人优先 quant.notify.to，
 * 其次用户资料邮箱（经 common 的 MailRecipientProvider SPI 反转依赖取得）。
 * 模板用 Thymeleaf 渲染 HTML；发送成功后回写 notified_flag 防止重复通知。
 */
@Service
public class NotifyServiceImpl implements NotifyService {

    /** 信号摘要邮件模板路径（classpath:templates/ 下） */
    private static final String TEMPLATE_DIGEST = "mail/signal-digest";

    /** 测试邮件模板路径 */
    private static final String TEMPLATE_TEST = "mail/test-mail";

    /** 同步异常告警邮件模板路径 */
    private static final String TEMPLATE_SYNC_ALERT = "mail/sync-alert";

    /** 发送失败后的重试次数（FR7：重试 2 次） */
    private static final int RETRY_TIMES = 2;

    /** 重试间隔（毫秒） */
    private static final long RETRY_BACKOFF_MS = 2000L;

    /** 补发窗口：只补发近 N 天内未通知的信号，避免误发陈旧建议 */
    private static final int PENDING_WINDOW_DAYS = 7;

    /** 信号方向常量 */
    private static final String DIRECTION_BUY = "BUY";

    private static final String DIRECTION_SELL = "SELL";

    private static final Logger LOGGER = LoggerFactory.getLogger(NotifyServiceImpl.class);

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    /** 收件人提供方（quant-system 实现；模块未装配时优雅降级） */
    private final ObjectProvider<MailRecipientProvider> recipientProvider;

    private final SpringTemplateEngine templateEngine;

    private final NotifyProperties properties;

    private final SignalRecordMapper signalRecordMapper;

    private final StrategyRegistry registry;

    /** SMTP 服务器地址（空=未配置） */
    @Value("${spring.mail.host:}")
    private String mailHost;

    /** SMTP 端口 */
    @Value("${spring.mail.port:465}")
    private Integer mailPort;

    /** SMTP 登录账号（同时是默认发件人） */
    @Value("${spring.mail.username:}")
    private String mailUsername;

    public NotifyServiceImpl(ObjectProvider<JavaMailSender> mailSenderProvider,
                             ObjectProvider<MailRecipientProvider> recipientProvider,
                             SpringTemplateEngine templateEngine, NotifyProperties properties,
                             SignalRecordMapper signalRecordMapper, StrategyRegistry registry) {
        this.mailSenderProvider = mailSenderProvider;
        this.recipientProvider = recipientProvider;
        this.templateEngine = templateEngine;
        this.properties = properties;
        this.signalRecordMapper = signalRecordMapper;
        this.registry = registry;
    }

    @Override
    public void sendTestMail() {
        Map<String, Object> model = new HashMap<>();
        model.put("time", TIME_FMT.format(java.time.LocalDateTime.now()));
        model.put("to", recipient());
        sendHtml("【量化投资平台】测试邮件", TEMPLATE_TEST, model);
    }

    @Override
    @Async("taskExecutor")
    public void sendSignalDigest(List<SignalRecord> signals) {
        if (!properties.isEnabled()) {
            return;
        }
        List<SignalRecord> actionable = signals.stream()
                .filter(this::needsMail)
                .toList();
        sendDigest(actionable);
    }

    @Override
    public int sendPendingDigest() {
        if (!properties.isEnabled()) {
            throw new BizException("邮件通知已关闭（quant.notify.enabled=false），如需发送请先在配置中开启");
        }
        List<SignalRecord> pending = signalRecordMapper.selectList(new LambdaQueryWrapper<SignalRecord>()
                .in(SignalRecord::getDirection, List.of(DIRECTION_BUY, DIRECTION_SELL))
                .eq(SignalRecord::getNotifiedFlag, 0)
                .ge(SignalRecord::getSignalDate, LocalDate.now().minusDays(PENDING_WINDOW_DAYS))
                .orderByDesc(SignalRecord::getSignalDate).orderByAsc(SignalRecord::getFundCode));
        if (pending.isEmpty()) {
            return 0;
        }
        return sendDigest(pending) ? pending.size() : 0;
    }

    /**
     * 摘要邮件统一发送路径：渲染 → 重试发送 → 成功后回写已通知标记。
     *
     * @param actionable 待通知信号（调用方已过滤方向与已通知标记）
     * @return true=发送成功
     */
    private boolean sendDigest(List<SignalRecord> actionable) {
        if (actionable.isEmpty()) {
            return false;
        }
        String date = actionable.get(0).getSignalDate().format(DATE_FMT);
        Map<String, Object> model = new HashMap<>();
        model.put("date", date);
        model.put("signals", toRows(actionable));
        boolean sent = sendWithRetry("【量化投资平台】" + date + " 交易信号", TEMPLATE_DIGEST, model);
        if (sent) {
            markNotified(actionable);
        }
        return sent;
    }

    @Override
    @Async("taskExecutor")
    public void sendSyncAlert(List<DashboardOverviewVO.SyncStatusItem> lagging) {
        if (!properties.isEnabled() || lagging == null || lagging.isEmpty()) {
            return;
        }
        List<Map<String, String>> rows = new ArrayList<>(lagging.size());
        for (DashboardOverviewVO.SyncStatusItem item : lagging) {
            Map<String, String> row = new HashMap<>();
            row.put("fund", item.fundCode() + " " + item.fundName());
            row.put("type", Integer.valueOf(1).equals(item.fundType()) ? "场内ETF" : "场外指数基金");
            row.put("last", item.lastDataDate() == null ? "无数据" : item.lastDataDate());
            row.put("expected", item.expectedDate());
            rows.add(row);
        }
        Map<String, Object> model = new HashMap<>();
        model.put("time", TIME_FMT.format(java.time.LocalDateTime.now()));
        model.put("lagging", rows);
        sendWithRetry("【量化投资平台】数据同步异常告警（" + lagging.size() + " 只）", TEMPLATE_SYNC_ALERT, model);
    }

    @Override
    public MailConfigVO mailConfig() {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        boolean configured = sender != null && mailHost != null && !mailHost.isBlank();
        return new MailConfigVO(properties.isEnabled(), emptyIfNull(mailHost), mailPort,
                mask(emptyIfNull(mailUsername)), resolveFrom(), recipientOrNull(), configured);
    }

    /**
     * 发送并重试：模板/配置类错误不重试（重试也不会成功），网络类错误最多重试 RETRY_TIMES 次。
     *
     * @return true=发送成功
     */
    private boolean sendWithRetry(String subject, String template, Map<String, Object> model) {
        for (int attempt = 0; attempt <= RETRY_TIMES; attempt++) {
            try {
                sendHtml(subject, template, model);
                LOGGER.info("邮件发送成功: subject={} to={}", subject, recipientOrNull());
                return true;
            } catch (BizException e) {
                if (isPermanent(e.getMessage()) || attempt == RETRY_TIMES) {
                    LOGGER.error("邮件发送失败（第{}次，终止）: subject={} reason={}", attempt + 1, subject, e.getMessage());
                    return false;
                }
                LOGGER.warn("邮件发送失败（第{}次，{}ms 后重试）: {}", attempt + 1, RETRY_BACKOFF_MS,
                        e.getMessage());
                sleepQuietly();
            }
        }
        return false;
    }

    /** 配置类错误（未配置/收件人缺失/组装失败）重试无意义，直接终止 */
    private boolean isPermanent(String message) {
        return message != null && (message.contains("未配置") || message.contains("组装失败"));
    }

    /** 重试等待（重试间隔，避免瞬时网络抖动即放弃） */
    private void sleepQuietly() {
        try {
            Thread.sleep(RETRY_BACKOFF_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 需要邮件通知的信号：方向为 BUY/SELL 且尚未通知过（HOLD 仅站内展示） */
    private boolean needsMail(SignalRecord signal) {
        return (DIRECTION_BUY.equals(signal.getDirection()) || DIRECTION_SELL.equals(signal.getDirection()))
                && Integer.valueOf(0).equals(signal.getNotifiedFlag());
    }

    /** 发送 HTML 邮件（统一装配收件人/发件人与异常转译） */
    private void sendHtml(String subject, String template, Map<String, Object> model) {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null || mailHost == null || mailHost.isBlank()) {
            throw new BizException("邮件服务未配置：请在配置文件设置 spring.mail.* 参数");
        }
        String to = recipient();
        String from = resolveFrom();
        try {
            Context context = new Context();
            model.forEach(context::setVariable);
            String html = templateEngine.process(template, context);
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            sender.send(message);
        } catch (MessagingException e) {
            throw new BizException("邮件组装失败: " + e.getMessage());
        } catch (MailException e) {
            throw new BizException("邮件发送失败（检查 SMTP 地址/端口/授权码）: " + e.getMessage());
        }
    }

    /** 通知行视图（record 访问器与模板取值兼容性差，统一转 Map） */
    private List<Map<String, String>> toRows(List<SignalRecord> signals) {
        List<Map<String, String>> rows = new ArrayList<>(signals.size());
        for (SignalRecord signal : signals) {
            Map<String, String> row = new HashMap<>();
            row.put("fund", signal.getFundCode());
            row.put("strategy", strategyName(signal.getStrategyType()));
            row.put("direction", signal.getDirection());
            row.put("price", signal.getPriceAt() == null ? "-" : signal.getPriceAt().toPlainString());
            row.put("desc", signal.getSuggestDesc() == null ? "" : signal.getSuggestDesc());
            rows.add(row);
        }
        return rows;
    }

    /** 策略展示名（未知类型回退为类型码） */
    private String strategyName(String strategyType) {
        try {
            return registry.getRequired(strategyType).name();
        } catch (BizException e) {
            return strategyType;
        }
    }

    /** 发送成功后标记已通知，避免下轮重复发送 */
    private void markNotified(List<SignalRecord> signals) {
        for (SignalRecord signal : signals) {
            SignalRecord record = signalRecordMapper.selectById(signal.getId());
            if (record != null && Integer.valueOf(0).equals(record.getNotifiedFlag())) {
                record.setNotifiedFlag(1);
                signalRecordMapper.updateById(record);
            }
        }
    }

    /** 收件人：配置优先，其次用户资料邮箱；均缺失抛错（发送路径用） */
    private String recipient() {
        String to = recipientOrNull();
        if (to == null) {
            throw new BizException("收件人未配置：请设置 quant.notify.to 或在平台配置维护通知邮箱");
        }
        return to;
    }

    /** 宽松版收件人解析（配置卡片展示用，缺失返回 null 不抛错） */
    private String recipientOrNull() {
        if (properties.getTo() != null && !properties.getTo().isBlank()) {
            return properties.getTo().trim();
        }
        MailRecipientProvider provider = recipientProvider.getIfAvailable();
        return provider == null ? null : provider.notifyRecipient();
    }

    /** 发件人：quant.notify.from 优先，否则 SMTP 登录账号 */
    private String resolveFrom() {
        if (properties.getFrom() != null && !properties.getFrom().isBlank()) {
            return properties.getFrom().trim();
        }
        return emptyIfNull(mailUsername);
    }

    /** 账号脱敏：保留前两字符与邮箱域名，其余以 *** 展示 */
    private String mask(String username) {
        if (username == null || username.isBlank()) {
            return "";
        }
        int at = username.indexOf('@');
        if (at <= 0) {
            return username.length() <= 2 ? "***" : username.substring(0, 2) + "***";
        }
        String prefix = username.substring(0, at);
        String shown = prefix.length() <= 2 ? prefix.charAt(0) + "***" : prefix.substring(0, 2) + "***";
        return shown + username.substring(at);
    }

    private String emptyIfNull(String value) {
        return value == null ? "" : value;
    }
}
