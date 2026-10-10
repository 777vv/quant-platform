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
import com.quant.strategy.entity.SysMailConfig;
import com.quant.strategy.mapper.SignalRecordMapper;
import com.quant.strategy.mapper.SysMailConfigMapper;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

/**
 * 邮件通知实现（M4-04；V4.9 起配置入库）：
 * SMTP 参数与开关来自 sys_mail_config 单行（平台配置页维护，保存即生效），
 * 收件人优先库内 to_addr，其次用户资料邮箱（经 common 的 MailRecipientProvider SPI 反转依赖取得）。
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

    /** 仓位配置偏离告警邮件模板路径（V5.36） */
    private static final String TEMPLATE_ALLOCATION = "mail/allocation-alert";

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

    /** 邮件配置（库内单行）与运行时发送器 */
    private final MailConfigService mailConfigService;

    /** 收件人提供方（quant-system 实现；模块未装配时优雅降级） */
    private final ObjectProvider<MailRecipientProvider> recipientProvider;

    private final SpringTemplateEngine templateEngine;

    private final SignalRecordMapper signalRecordMapper;

    private final StrategyRegistry registry;

    private final SysMailConfigMapper mapper;

    public NotifyServiceImpl(MailConfigService mailConfigService,
                             SysMailConfigMapper mapper,
                             ObjectProvider<MailRecipientProvider> recipientProvider,
                             SpringTemplateEngine templateEngine,
                             SignalRecordMapper signalRecordMapper, StrategyRegistry registry) {
        this.mailConfigService = mailConfigService;
        this.mapper = mapper;
        this.recipientProvider = recipientProvider;
        this.templateEngine = templateEngine;
        this.signalRecordMapper = signalRecordMapper;
        this.registry = registry;
    }

    @Override
    public void sendTestMail(MailConfigRequest request) {
        SysMailConfig stored = mailConfigService.config();
        // 按"界面当前填写"测试（V5.26 用户口径：表单改了没保存，测试的也应是眼前这套），
        // 留空或打码的字段回退库内已存值——打码回显（83***@qq.com / ****）不是真值，绝不能当真值去连 SMTP。
        SysMailConfig effective = new SysMailConfig();
        effective.setHost(firstText(request == null ? null : request.host(), stored.getHost()));
        effective.setPort(request != null && request.port() != null ? request.port()
                : (stored.getPort() == null ? Integer.valueOf(465) : stored.getPort()));
        effective.setUsername(keepIfMasked(request == null ? null : request.username(), stored.getUsername()));
        effective.setPassword(keepIfMasked(request == null ? null : request.password(), stored.getPassword()));
        if (!notBlank(effective.getHost()) || !notBlank(effective.getUsername())) {
            throw new BizException("请先填写 SMTP 服务器与登录账号再测试");
        }
        String to = firstText(request == null ? null : request.toAddr(), stored.getToAddr(), recipientOrNull());
        if (to == null) {
            throw new BizException("收件人未配置：请填写收件人，或在基本信息维护通知邮箱");
        }
        String from = firstText(request == null ? null : request.fromAddr(), stored.getFromAddr(),
                effective.getUsername());
        Map<String, Object> model = new HashMap<>();
        model.put("time", TIME_FMT.format(java.time.LocalDateTime.now()));
        model.put("to", to);
        // 一次性发送器：不读也不写 mailConfigService 的缓存，测试全程不落库
        sendVia(mailConfigService.senderFor(effective), from, to,
                "【策略数据研究平台】测试邮件", TEMPLATE_TEST, model);
        // 测试发送是同步接口，成功必须落日志：便于用户在日志里自查是发出去了还是被上游拒了
        LOGGER.info("测试邮件发送成功: to={}（按界面当前填写测试，未改变已存配置）", to);
    }

    @Override
    @Async("taskExecutor")
    public void sendSignalDigest(List<SignalRecord> signals) {
        if (!mailEnabled()) {
            return;
        }
        List<SignalRecord> actionable = signals.stream()
                .filter(this::needsMail)
                .toList();
        sendDigest(actionable);
    }

    @Override
    public int sendPendingDigest() {
        if (!mailEnabled()) {
            throw new BizException("邮件通知已关闭：请在【平台配置 → 邮件通知】开启后再试");
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
        boolean sent = sendWithRetry("【策略数据研究平台】" + date + " 交易信号", TEMPLATE_DIGEST, model);
        if (sent) {
            markNotified(actionable);
        }
        return sent;
    }

    @Override
    @Async("taskExecutor")
    public void sendSyncAlert(List<DashboardOverviewVO.SyncStatusItem> lagging) {
        if (!mailEnabled() || lagging == null || lagging.isEmpty()) {
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
        sendWithRetry("【策略数据研究平台】数据同步异常告警（" + lagging.size() + " 只）", TEMPLATE_SYNC_ALERT, model);
    }

    @Override
    @Async("taskExecutor")
    public void sendAllocationAlert(com.quant.fund.dto.AllocationCheckVO result,
                                    java.util.List<com.quant.fund.dto.AllocationCheckVO.Row> violations) {
        Map<String, Object> model = new HashMap<>();
        model.put("time", TIME_FMT.format(java.time.LocalDateTime.now()));
        model.put("totalAssets", result.totalAssets() == null ? "--" : result.totalAssets().toPlainString());
        model.put("cash", result.cashBalance() == null ? "--" : result.cashBalance().toPlainString());
        // 明细行统一转 Map（与信号摘要同一套路，避免 record 与模板取值的兼容性问题）
        List<Map<String, String>> rows = new ArrayList<>();
        for (com.quant.fund.dto.AllocationCheckVO.Row row : result.rows()) {
            Map<String, String> m = new HashMap<>();
            m.put("label", row.label());
            m.put("amount", row.amount() == null ? "--" : row.amount().toPlainString());
            m.put("current", row.currentPct() == null ? "--" : row.currentPct().toPlainString());
            m.put("range", row.minPct().toPlainString() + "% ~ " + row.maxPct().toPlainString() + "%");
            m.put("ok", row.ok() ? "✓ 在范围内" : "✗ 越界");
            m.put("bad", row.ok() ? "0" : "1");
            rows.add(m);
        }
        model.put("rows", rows);
        sendWithRetry("【策略数据研究平台】仓位配置偏离告警（" + violations.size() + " 类越界）",
                TEMPLATE_ALLOCATION, model);
    }

    @Override
    public MailConfigVO mailConfig() {
        SysMailConfig config = mailConfigService.config();
        boolean enabled = Integer.valueOf(1).equals(config.getEnabled());
        boolean configured = mailConfigService.isConfigured();
        return new MailConfigVO(enabled, emptyIfNull(config.getHost()), config.getPort(),
                mask(emptyIfNull(config.getUsername())), resolveFrom(), recipientOrNull(), configured);
    }

    @Override
    public void saveMailConfig(MailConfigRequest request) {
        if (request == null) {
            throw new BizException("配置内容不能为空");
        }
        if (request.port() != null && (request.port() <= 0 || request.port() > 65535)) {
            throw new BizException("SMTP 端口需在 1~65535 之间");
        }
        SysMailConfig current = mailConfigService.config();
        // 留空或回传打码值（含 *）= 不修改已存值：账号与授权码同一约定。
        // 账号必须一并如此处理——配置卡片回显的是 83***@qq.com 这类打码值，
        // 用户不动它直接保存时不能把这个展示值写回库（否则 SMTP 认证必然失败）。
        String username = keepIfMasked(request.username(), current.getUsername());
        String password = keepIfMasked(request.password(), current.getPassword());
        if (notBlank(request.host()) && (!notBlank(username) || !notBlank(password))) {
            throw new BizException("填写了 SMTP 服务器时，登录账号与授权码不能为空");
        }
        SysMailConfig row = new SysMailConfig();
        row.setId(1L);
        row.setEnabled(request.enabled() == null ? enabledFlag(current) : (request.enabled() ? 1 : 0));
        row.setHost(trimToNull(request.host()));
        row.setPort(request.port() == null ? current.getPort() : request.port());
        row.setUsername(trimToNull(username));
        row.setPassword(trimToNull(password));
        row.setFromAddr(trimToNull(request.fromAddr()));
        row.setToAddr(trimToNull(request.toAddr()));
        if (!rowExists()) {
            mapperInsert(row);
        } else {
            row.setUpdatedAt(java.time.LocalDateTime.now());
            row.setId(current.getId() == null ? 1L : current.getId());
            mapperUpdate(row);
        }
        mailConfigService.invalidate();
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
                    LOGGER.error("邮件发送失败（第{}次，终止）: subject={}", attempt + 1, subject, e);
                    return false;
                }
                LOGGER.error("邮件发送失败（第{}次，{}ms 后重试）: subject={}", attempt + 1, RETRY_BACKOFF_MS, subject,
                        e);
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

    /** 发送 HTML 邮件（按库内配置取发送器，统一装配收件人/发件人与异常转译） */
    private void sendHtml(String subject, String template, Map<String, Object> model) {
        JavaMailSender sender = mailConfigService.sender();
        if (sender == null) {
            throw new BizException("邮件服务未配置：请在【平台配置 → 邮件通知】填写 SMTP 服务器与账号后保存");
        }
        sendVia(sender, resolveFrom(), recipient(), subject, template, model);
    }

    /**
     * 用给定发送器发 HTML 邮件（testMail 传界面当前配置现建的发送器，其余走库内配置）。
     *
     * @param sender   发送器
     * @param from     发件人
     * @param to       收件人
     * @param subject  主题
     * @param template 模板路径
     * @param model    模板变量
     */
    private void sendVia(JavaMailSender sender, String from, String to,
            String subject, String template, Map<String, Object> model) {
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

    /** 收件人：库内配置优先，其次用户资料邮箱；均缺失抛错（发送路径用） */
    private String recipient() {
        String to = recipientOrNull();
        if (to == null) {
            throw new BizException("收件人未配置：请在【平台配置 → 邮件通知】填写收件人，或在基本信息维护通知邮箱");
        }
        return to;
    }

    /** 宽松版收件人解析（配置卡片展示用，缺失返回 null 不抛错） */
    private String recipientOrNull() {
        SysMailConfig config = mailConfigService.config();
        if (config.getToAddr() != null && !config.getToAddr().isBlank()) {
            return config.getToAddr().trim();
        }
        MailRecipientProvider provider = recipientProvider.getIfAvailable();
        return provider == null ? null : provider.notifyRecipient();
    }

    /** 发件人：库内 from_addr 优先，否则 SMTP 登录账号 */
    private String resolveFrom() {
        SysMailConfig config = mailConfigService.config();
        if (config.getFromAddr() != null && !config.getFromAddr().isBlank()) {
            return config.getFromAddr().trim();
        }
        return emptyIfNull(config.getUsername());
    }

    /** 持久化辅助：库内无行时插入 */
    private void mapperInsert(SysMailConfig row) {
        mapper.insert(row);
    }

    /** 持久化辅助：已有行时更新 */
    private void mapperUpdate(SysMailConfig row) {
        mapper.updateById(row);
    }

    /** 库内是否已有配置行（首行插入、其后更新） */
    private boolean rowExists() {
        return mapper.selectById(1L) != null;
    }

    /** 邮件通知是否开启（开关字段允许为 NULL，NULL 视为关闭，避免拆箱空指针） */
    private boolean mailEnabled() {
        return Integer.valueOf(1).equals(mailConfigService.config().getEnabled());
    }

    /** 开关字段归一为 0/1（NULL 归 0，保证入库不留空值） */
    private int enabledFlag(SysMailConfig config) {
        return Integer.valueOf(1).equals(config.getEnabled()) ? 1 : 0;
    }

    /** trim 到空即 null（避免把空白存进库） */
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 打码值（含 *）或留空表示「沿用已存值」：配置卡片回显账号/授权码都是打码的，
     * 用户不改动就保存时必须保留原值，否则会把展示值写回库。
     */
    private String keepIfMasked(String incoming, String current) {
        if (incoming == null || incoming.isBlank() || incoming.contains("*")) {
            return current;
        }
        return incoming;
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    /** 返回第一个非空白的值（全空返回 null）；测试发送合并"界面填写 > 库内已存"时用 */
    private String firstText(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate.trim();
            }
        }
        return null;
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
