package com.quant.ai.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.quant.ai.dto.AiQuotaRequest;
import com.quant.ai.dto.AiQuotaVO;
import com.quant.ai.dto.AiUsageDayVO;
import com.quant.ai.dto.AiUsageLogVO;
import com.quant.ai.dto.AiUsageRecord;
import com.quant.ai.dto.AiUsageTodayVO;
import com.quant.ai.entity.AiChatSession;
import com.quant.ai.entity.AiModelConfig;
import com.quant.ai.entity.AiRuntimeConfig;
import com.quant.ai.entity.AiUsageLog;
import com.quant.ai.enums.AiProviderEnum;
import com.quant.ai.enums.AiUsageBizEnum;
import com.quant.ai.mapper.AiChatSessionMapper;
import com.quant.ai.mapper.AiModelConfigMapper;
import com.quant.ai.mapper.AiRuntimeConfigMapper;
import com.quant.ai.mapper.AiUsageLogMapper;
import com.quant.ai.service.AiUsageService;
import com.quant.common.exception.BizException;
import com.quant.common.result.PageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * AI 用量与费用服务实现（V3.9 起）。
 *
 * <p>聚合方式：单人平台每日流水最多几百行，**按自然日把明细取回后在应用层汇总**——
 * 口径只有一处（本类），也不必为 SUM 写自定义 SQL；明细列表才走数据库分页。
 *
 * <p>费用口径：单价按「元/百万 token」填写，`费用 =（未命中缓存输入 × 输入单价 + 命中缓存输入 ×
 * 缓存命中单价 + 输出 × 输出单价）÷ 100 万`，保留 6 位小数（单次咨询常低于 1 分钱）。
 * 缓存命中单价填 0 时**回退按输入单价计**，避免命中部分被漏计（免费模型单价全 0，费用恒为 0）。
 *
 * <p>V4.0 起两组配置分家：**单价**从流水对应的那个厂商行取（`ai_model_config`，各厂价目不同），
 * **每日额度**从全局单行取（`ai_runtime_config`，不跟厂商走）。
 *
 * <p>⚠️ 这里直接注入 Mapper 而**不是** {@code AiModelConfigService}：后者要注入本服务去记录
 * 「平台配置页自检」的用量，走 Service 会形成循环依赖（启动即失败）。
 * 与 docs/02 里 FundQueryService 循环依赖的先例同一处理方式——用 Mapper 打断环。
 */
@Service
public class AiUsageServiceImpl implements AiUsageService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AiUsageServiceImpl.class);

    /** 单行运行时配置的固定主键（与 AiModelConfigServiceImpl 保持一致） */
    private static final Long RUNTIME_ID = 1L;

    /** 单价基数：单价按「元/百万 token」填写 */
    private static final BigDecimal PER_MILLION = new BigDecimal("1000000");

    /** 百分比基数 */
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /** 费用小数位（与 ai_usage_log.cost 定义一致） */
    private static final int COST_SCALE = 6;

    /** 趋势统计的最大天数（防止一次拉太多流水） */
    private static final int MAX_SUMMARY_DAYS = 90;

    /** 明细分页的单页上限 */
    private static final int MAX_PAGE_SIZE = 100;

    /** 已用比例展示上限（超限时可能出现几百倍的倍数，封顶后界面仍可读） */
    private static final int MAX_USED_PERCENT = 999;

    /** 失败原因入库长度上限（与 ai_usage_log.error_msg 定义一致，超长截断避免写入报错） */
    private static final int ERROR_MSG_MAX_LEN = 500;

    private final AiUsageLogMapper usageMapper;

    private final AiChatSessionMapper sessionMapper;

    private final AiModelConfigMapper configMapper;

    private final AiRuntimeConfigMapper runtimeMapper;

    public AiUsageServiceImpl(AiUsageLogMapper usageMapper, AiChatSessionMapper sessionMapper,
                              AiModelConfigMapper configMapper, AiRuntimeConfigMapper runtimeMapper) {
        this.usageMapper = usageMapper;
        this.sessionMapper = sessionMapper;
        this.configMapper = configMapper;
        this.runtimeMapper = runtimeMapper;
    }

    @Override
    public void record(AiUsageRecord record) {
        if (record == null) {
            return;
        }
        try {
            AiUsageLog row = buildRow(record);
            usageMapper.insert(row);
            // 回填费用：调用方（对话结束帧）要即时展示"本次 N token ≈ ¥X"
            record.setCost(row.getCost());
            LOGGER.info("AI 用量：{} 轮，输入 {} / 输出 {} / 合计 {} token，费用 {} 元（{}，{}）",
                    row.getRounds(), row.getPromptTokens(), row.getCompletionTokens(), row.getTotalTokens(),
                    row.getCost(), row.getBiz(), row.getModel());
        } catch (Exception e) {
            // 记账是旁路：写失败只记日志，绝不让对话因此失败
            LOGGER.warn("AI 用量流水写入失败（不影响本次对话）：{}", e.getMessage());
        }
    }

    @Override
    public AiUsageTodayVO today() {
        LocalDate date = LocalDate.now();
        List<AiUsageLog> rows = rowsOf(date);
        AiRuntimeConfig runtime = runtime();
        int tokenLimit = tokenLimitOf(runtime);
        BigDecimal costLimit = costLimitOf(runtime);
        int warnPercent = warnPercentOf(runtime);

        long prompt = 0L;
        long cached = 0L;
        long completion = 0L;
        BigDecimal cost = BigDecimal.ZERO;
        int chatCount = 0;
        boolean estimated = false;
        for (AiUsageLog row : rows) {
            prompt += number(row.getPromptTokens());
            cached += number(row.getCachedTokens());
            completion += number(row.getCompletionTokens());
            cost = cost.add(nzMoney(row.getCost()));
            if (AiUsageBizEnum.CHAT.name().equals(row.getBiz())) {
                chatCount++;
            }
            estimated = estimated || Integer.valueOf(1).equals(row.getEstimated());
        }
        long total = prompt + completion;
        // "未填单价"看**当前启用厂商**那一行：免费模型三个价都为 0 也算（费用恒为 0，费用限额不生效）
        AiModelConfig activeConfig = providerConfig(runtime.getActiveProvider());
        boolean priceMissing = nzMoney(activeConfig.getInputPrice()).signum() == 0
                && nzMoney(activeConfig.getOutputPrice()).signum() == 0;
        boolean overLimit = overLimit(total, cost, tokenLimit, costLimit);
        int usedPercent = usedPercent(total, cost, tokenLimit, costLimit);
        boolean warn = !overLimit && warnPercent > 0 && usedPercent >= warnPercent;
        return new AiUsageTodayVO(date, rows.size(), chatCount, total, prompt, cached, completion,
                cost.setScale(COST_SCALE, RoundingMode.HALF_UP), estimated,
                runtime.getDailyTokenLimit(), runtime.getDailyCostLimit(), warnPercent,
                usedPercent, warn, overLimit, priceMissing, hint(overLimit, priceMissing));
    }

    @Override
    public List<AiUsageDayVO> summary(int days) {
        int span = Math.min(Math.max(days, 1), MAX_SUMMARY_DAYS);
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(span - 1L);
        List<AiUsageLog> rows = usageMapper.selectList(new LambdaQueryWrapper<AiUsageLog>()
                .ge(AiUsageLog::getCreatedAt, from.atStartOfDay())
                .lt(AiUsageLog::getCreatedAt, today.plusDays(1).atStartOfDay()));
        Map<LocalDate, DayAgg> grouped = new HashMap<>(span);
        for (AiUsageLog row : rows) {
            if (row.getCreatedAt() == null) {
                continue;
            }
            LocalDate date = row.getCreatedAt().toLocalDate();
            grouped.merge(date, new DayAgg(number(row.getTotalTokens()), nzMoney(row.getCost()), 1), DayAgg::plus);
        }
        // 没有流水的日期补 0：前端可直接按下标画柱状图，不必自己补齐空洞
        List<AiUsageDayVO> list = new ArrayList<>(span);
        for (LocalDate date = from; !date.isAfter(today); date = date.plusDays(1)) {
            DayAgg agg = grouped.get(date);
            list.add(agg == null ? new AiUsageDayVO(date, 0, 0L, BigDecimal.ZERO.setScale(COST_SCALE))
                    : new AiUsageDayVO(date, agg.count(), agg.tokens(), agg.cost().setScale(COST_SCALE, RoundingMode.HALF_UP)));
        }
        return list;
    }

    @Override
    public PageResult<AiUsageLogVO> logs(long page, long size, LocalDate date) {
        long current = Math.max(page, 1L);
        long limit = Math.min(Math.max(size, 1L), MAX_PAGE_SIZE);
        LocalDateTime start = date == null ? null : date.atStartOfDay();
        LocalDateTime end = date == null ? null : date.plusDays(1).atStartOfDay();
        IPage<AiUsageLog> result = usageMapper.selectPage(new Page<>(current, limit),
                new LambdaQueryWrapper<AiUsageLog>()
                        .ge(date != null, AiUsageLog::getCreatedAt, start)
                        .lt(date != null, AiUsageLog::getCreatedAt, end)
                        .orderByDesc(AiUsageLog::getId));
        Map<String, String> titles = titlesOf(result.getRecords());
        return PageResult.of(result.getTotal(),
                result.getRecords().stream().map(row -> toVO(row, titles)).toList());
    }

    @Override
    public void checkQuota() {
        AiRuntimeConfig runtime = runtime();
        int tokenLimit = tokenLimitOf(runtime);
        BigDecimal costLimit = costLimitOf(runtime);
        if (tokenLimit <= 0 && costLimit.signum() <= 0) {
            // 两个维度都没限制：连查询都省掉
            return;
        }
        AiUsageTodayVO today = today();
        if (!today.overLimit()) {
            return;
        }
        // 只列出"设了上限"的维度，避免出现"上限 token / ¥0"这种废信息
        List<String> used = new ArrayList<>(2);
        List<String> limits = new ArrayList<>(2);
        if (tokenLimit > 0) {
            used.add(String.format("%,d token", today.totalTokens()));
            limits.add(String.format("%,d token", tokenLimit));
        }
        if (costLimit.signum() > 0) {
            used.add("¥" + today.cost().toPlainString());
            limits.add("¥" + costLimit.toPlainString());
        }
        throw new BizException("今日 AI 用量已达上限（已用 " + String.join(" / ", used)
                + "，上限 " + String.join(" / ", limits)
                + "），明日 00:00 自动恢复；可在【AI用量统计】页调高上限");
    }

    @Override
    public AiQuotaVO quota() {
        AiRuntimeConfig runtime = runtime();
        return new AiQuotaVO(runtime.getDailyTokenLimit(), runtime.getDailyCostLimit(),
                warnPercentOf(runtime));
    }

    @Override
    public void saveQuota(AiQuotaRequest request) {
        if (request == null) {
            throw new BizException("额度内容不能为空");
        }
        if (request.dailyTokenLimit() != null && request.dailyTokenLimit() < 0) {
            throw new BizException("每日 token 上限不能为负数（填 0 表示不限制）");
        }
        if (request.dailyCostLimit() != null && request.dailyCostLimit().signum() < 0) {
            throw new BizException("每日费用上限不能为负数（填 0 表示不限制）");
        }
        if (request.warnPercent() != null && (request.warnPercent() < 0 || request.warnPercent() > 100)) {
            throw new BizException("预警百分比需在 0~100 之间（填 0 表示不预警）");
        }
        AiRuntimeConfig runtime = runtimeMapper.selectById(RUNTIME_ID);
        AiRuntimeConfig update = new AiRuntimeConfig();
        update.setId(RUNTIME_ID);
        // null 表示"保持原值"（MyBatis-Plus 跳过 null 字段），0 是合法值（= 该维度不限制）
        update.setDailyTokenLimit(request.dailyTokenLimit());
        update.setDailyCostLimit(request.dailyCostLimit());
        update.setWarnPercent(request.warnPercent());
        if (runtime == null) {
            runtimeMapper.insert(update);
        } else {
            runtimeMapper.updateById(update);
        }
        LOGGER.info("AI 每日额度已更新：token={} / 费用={} / 预警={}%",
                request.dailyTokenLimit(), request.dailyCostLimit(), request.warnPercent());
    }

    /** 组装流水实体（费用与三个单价快照在此计算，历史行不受后续改价影响） */
    private AiUsageLog buildRow(AiUsageRecord record) {
        AiRuntimeConfig runtime = runtime();
        // 单价取"这条流水所属厂商"的价格：自检可能探测别家（record.provider 已指定），对话则用当前启用厂商
        String provider = notBlank(record.getProvider()) ? record.getProvider() : runtime.getActiveProvider();
        AiModelConfig config = providerConfig(provider);
        BigDecimal inputPrice = nzMoney(config.getInputPrice());
        BigDecimal outputPrice = nzMoney(config.getOutputPrice());
        BigDecimal rawCachePrice = nzMoney(config.getCacheInputPrice());
        // 缓存命中单价填 0 时回退按输入单价计：否则命中部分会被算成免费，费用被低估
        BigDecimal cachePrice = rawCachePrice.signum() > 0 ? rawCachePrice : inputPrice;
        int prompt = Math.max(record.getPromptTokens(), 0);
        int cached = Math.min(Math.max(record.getCachedTokens(), 0), prompt);
        int completion = Math.max(record.getCompletionTokens(), 0);

        AiUsageLog row = new AiUsageLog();
        row.setSessionId(record.getSessionId());
        row.setBiz(record.getBiz());
        row.setProvider(notBlank(record.getProvider()) ? record.getProvider() : config.getProvider());
        row.setModel(notBlank(record.getModel()) ? record.getModel() : config.getModel());
        row.setRounds(Math.max(record.getRounds(), 1));
        row.setPromptTokens(prompt);
        row.setCachedTokens(cached);
        row.setCompletionTokens(completion);
        row.setTotalTokens(prompt + completion);
        row.setEstimated(record.isEstimated() ? 1 : 0);
        row.setInputPrice(inputPrice);
        row.setCacheInputPrice(cachePrice);
        row.setOutputPrice(outputPrice);
        row.setCost(cost(prompt, cached, completion, inputPrice, cachePrice, outputPrice));
        row.setQuestionChars(Math.max(record.getQuestionChars(), 0));
        row.setAnswerChars(Math.max(record.getAnswerChars(), 0));
        row.setDurationMs(record.getDurationMs());
        row.setStatus(record.getStatus());
        row.setErrorMsg(truncate(record.getErrorMsg()));
        row.setCreatedAt(LocalDateTime.now());
        return row;
    }

    /** 三段计价：未命中缓存输入 × 输入单价 + 命中缓存输入 × 缓存单价 + 输出 × 输出单价 */
    private BigDecimal cost(int prompt, int cached, int completion,
                            BigDecimal inputPrice, BigDecimal cachePrice, BigDecimal outputPrice) {
        BigDecimal missInput = BigDecimal.valueOf(prompt - (long) cached);
        BigDecimal total = missInput.multiply(inputPrice)
                .add(BigDecimal.valueOf(cached).multiply(cachePrice))
                .add(BigDecimal.valueOf(completion).multiply(outputPrice));
        return total.divide(PER_MILLION, COST_SCALE, RoundingMode.HALF_UP);
    }

    /** 今日流水（按自然日：>= 今日 00:00 且 < 明日 00:00） */
    private List<AiUsageLog> rowsOf(LocalDate date) {
        return usageMapper.selectList(new LambdaQueryWrapper<AiUsageLog>()
                .ge(AiUsageLog::getCreatedAt, date.atStartOfDay())
                .lt(AiUsageLog::getCreatedAt, date.plusDays(1).atStartOfDay())
                .orderByAsc(AiUsageLog::getId));
    }

    /** 全局运行时配置（当前厂商 + 额度；尚未保存过时返回空壳：未设厂商、额度视作 0 = 不限制） */
    private AiRuntimeConfig runtime() {
        AiRuntimeConfig runtime = runtimeMapper.selectById(RUNTIME_ID);
        return runtime == null ? new AiRuntimeConfig() : runtime;
    }

    /** 某厂商的模型配置（用于单价；缺行或未指定厂商时返回空壳，单价按 0 计） */
    private AiModelConfig providerConfig(String provider) {
        if (!notBlank(provider)) {
            return new AiModelConfig();
        }
        AiModelConfig config = configMapper.selectOne(new LambdaQueryWrapper<AiModelConfig>()
                .eq(AiModelConfig::getProvider, provider.trim()));
        return config == null ? new AiModelConfig() : config;
    }

    /** 会话标题映射（一次查询取回本页所有会话的标题，避免逐行查库） */
    private Map<String, String> titlesOf(List<AiUsageLog> rows) {
        List<String> sessionIds = rows.stream().map(AiUsageLog::getSessionId)
                .filter(this::notBlank).distinct().toList();
        if (sessionIds.isEmpty()) {
            return Map.of();
        }
        return sessionMapper.selectList(new LambdaQueryWrapper<AiChatSession>()
                        .in(AiChatSession::getSessionId, sessionIds))
                .stream()
                .collect(Collectors.toMap(AiChatSession::getSessionId,
                        session -> session.getTitle() == null ? "" : session.getTitle(), (first, second) -> first));
    }

    /** 明细行 → 视图对象 */
    private AiUsageLogVO toVO(AiUsageLog row, Map<String, String> titles) {
        String biz = row.getBiz();
        AiUsageBizEnum bizEnum = bizEnumOf(biz);
        return new AiUsageLogVO(row.getId(), row.getSessionId(), titles.get(row.getSessionId()),
                biz, bizEnum == null ? biz : bizEnum.getDisplayName(),
                row.getProvider(), AiProviderEnum.of(row.getProvider()).getDisplayName(), row.getModel(),
                number(row.getRounds()), number(row.getPromptTokens()), number(row.getCachedTokens()),
                number(row.getCompletionTokens()), number(row.getTotalTokens()),
                Integer.valueOf(1).equals(row.getEstimated()), nzMoney(row.getCost()),
                number(row.getQuestionChars()), number(row.getAnswerChars()), row.getDurationMs(),
                Integer.valueOf(1).equals(row.getStatus()), row.getErrorMsg(), row.getCreatedAt());
    }

    /** 是否超限（任一维度达到上限即算超限，"达到"而不是"超过"） */
    private boolean overLimit(long totalTokens, BigDecimal cost, int tokenLimit, BigDecimal costLimit) {
        boolean tokenOver = tokenLimit > 0 && totalTokens >= tokenLimit;
        boolean costOver = costLimit.signum() > 0 && cost.compareTo(costLimit) >= 0;
        return tokenOver || costOver;
    }

    /** 已用比例：token 与费用两个口径取大者（用于进度条与预警；封顶 999% 便于展示，真实倍数看已用/上限） */
    private int usedPercent(long totalTokens, BigDecimal cost, int tokenLimit, BigDecimal costLimit) {
        int tokenPercent = tokenLimit > 0 ? (int) (totalTokens * 100L / tokenLimit) : 0;
        int costPercent = costLimit.signum() > 0
                ? cost.multiply(HUNDRED).divide(costLimit, 0, RoundingMode.DOWN).intValue() : 0;
        return Math.min(Math.max(tokenPercent, costPercent), MAX_USED_PERCENT);
    }

    /** 概览提示文案（未填单价优先提示：此时费用限额实际不生效） */
    private String hint(boolean overLimit, boolean priceMissing) {
        if (priceMissing) {
            return "当前厂商未填写单价：新产生的用量费用按 0 元计（历史流水保留当时的单价快照），"
                    + "按费用的限额因此不生效；要按费用控额度，请在【平台配置 → AI 模型配置】填该厂商单价（元/百万 token）";
        }
        if (overLimit) {
            return "今日额度已用完，新提问会被拒绝，明日 00:00 自动恢复";
        }
        return null;
    }

    /** 每日 token 上限（null 视作 0 = 不限制） */
    private int tokenLimitOf(AiRuntimeConfig runtime) {
        return number(runtime.getDailyTokenLimit());
    }

    /** 每日费用上限（null 视作 0 = 不限制） */
    private BigDecimal costLimitOf(AiRuntimeConfig runtime) {
        return nzMoney(runtime.getDailyCostLimit());
    }

    /** 预警百分比（null 或越界视作 0 = 不预警） */
    private int warnPercentOf(AiRuntimeConfig runtime) {
        int percent = number(runtime.getWarnPercent());
        return percent < 0 || percent > 100 ? 0 : percent;
    }

    /** 用途代码 → 枚举（未知代码返回 null，展示时回退原代码） */
    private AiUsageBizEnum bizEnumOf(String biz) {
        for (AiUsageBizEnum item : AiUsageBizEnum.values()) {
            if (item.name().equals(biz)) {
                return item;
            }
        }
        return null;
    }

    /** 失败原因截断（超长会写库报错） */
    private String truncate(String errorMsg) {
        if (errorMsg == null) {
            return null;
        }
        return errorMsg.length() <= ERROR_MSG_MAX_LEN ? errorMsg : errorMsg.substring(0, ERROR_MSG_MAX_LEN);
    }

    private int number(Integer value) {
        return value == null ? 0 : value;
    }

    private BigDecimal nzMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    /** 单日汇总（按日聚合的中间结果） */
    private record DayAgg(long tokens, BigDecimal cost, int count) {

        /** 合并同一天的两条记录 */
        DayAgg plus(DayAgg other) {
            return new DayAgg(tokens + other.tokens, cost.add(other.cost), count + other.count);
        }
    }
}
