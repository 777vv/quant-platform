package com.quant.ai.service.impl;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.quant.ai.config.AiConfig.AiChatClientFactory;
import com.quant.ai.dto.AiModelConfigRequest;
import com.quant.ai.dto.AiModelConfigVO;
import com.quant.ai.dto.AiModelOptionVO;
import com.quant.ai.dto.AiProviderVO;
import com.quant.ai.dto.AiUsageRecord;
import com.quant.ai.entity.AiModelConfig;
import com.quant.ai.entity.AiRuntimeConfig;
import com.quant.ai.enums.AiProviderEnum;
import com.quant.ai.enums.AiUsageBizEnum;
import com.quant.ai.mapper.AiModelConfigMapper;
import com.quant.ai.mapper.AiRuntimeConfigMapper;
import com.quant.ai.service.AiModelConfigService;
import com.quant.ai.service.AiUsageService;
import com.quant.common.exception.BizException;
import com.quant.common.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * AI 模型配置服务实现（V4.0：**每个厂商一行**）。
 *
 * <p>Token 只在本类与工厂之间流转：对外 VO 一律打码（形如 `sk-****cdef`），
 * 保存时若前端回传的是打码值或空串，则保留**该厂商**已存 Token（避免"看着打码值保存把 Key 覆盖掉"）。
 *
 * <p>为什么改成每厂商一行：单行存储时"换厂商保存"必然覆盖上一家的 Base URL/Token/模型/单价，
 * 用户切回旧厂商得重填。现在每厂商一行、互不覆盖，"保存并生效"只是把
 * {@code ai_runtime_config.active_provider} 指向刚保存的那家——**选中厂商 + 保存 = 切换厂商**。
 *
 * <p>顺带简化：V3.x 那条"跨厂商不允许沿用 Token"的拦截不再需要——Token 本来就按厂商各存，
 * 不存在"拿错家 Key"的机会了。
 *
 * <p>单价是 per-provider 的（各厂价目不同），写在 ai_model_config；
 * 每日额度是全局的（不跟厂商走），在 ai_runtime_config，由 {@code AiUsageService} 读写。
 */
@Service
public class AiModelConfigServiceImpl implements AiModelConfigService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AiModelConfigServiceImpl.class);

    /** 单行运行时配置的固定主键 */
    private static final Long RUNTIME_ID = 1L;

    /** 拉取模型列表 / 连通性自检的超时（秒）：避免上游挂死把页面卡住 */
    private static final int HTTP_TIMEOUT_SECONDS = 15;

    /** 打码时保留的尾部字符数 */
    private static final int MASK_KEEP_TAIL = 4;

    /** 连通性自检的固定提问（与 /api/ai/health 一致，用作用量统计的输入字符基准） */
    private static final String HEALTH_QUESTION = "请回复两个字：正常";

    private final AiModelConfigMapper configMapper;

    private final AiRuntimeConfigMapper runtimeMapper;

    private final AiChatClientFactory clientFactory;

    /** 用量服务：自检也会真实消耗 token，故与对话同口径记一行流水 */
    private final AiUsageService usageService;

    public AiModelConfigServiceImpl(AiModelConfigMapper configMapper, AiRuntimeConfigMapper runtimeMapper,
                                    AiChatClientFactory clientFactory, AiUsageService usageService) {
        this.configMapper = configMapper;
        this.runtimeMapper = runtimeMapper;
        this.clientFactory = clientFactory;
        this.usageService = usageService;
    }

    @Override
    public AiModelConfig snapshot() {
        AiModelConfig active = configOf(activeProviderCode(), false);
        if (active != null && notBlank(active.getApiKey())) {
            return active;
        }
        // 当前厂商没设或没配 Token：退化为"第一个已配 Token 的厂商"，让历史/异常数据自愈
        AiModelConfig fallback = firstConfigured();
        if (fallback != null) {
            return fallback;
        }
        // 完全未配置：返回带厂商默认端点的空壳，便于前端首次填写
        AiModelConfig empty = new AiModelConfig();
        empty.setProvider(AiProviderEnum.ZHIPU.name());
        empty.setBaseUrl(AiProviderEnum.ZHIPU.getDefaultBaseUrl());
        empty.setModel("");
        empty.setApiKey("");
        return empty;
    }

    @Override
    public AiModelConfigVO view(String provider) {
        AiProviderEnum providerEnum = AiProviderEnum.of(notBlank(provider) ? provider : activeProviderCode());
        AiModelConfig config = configOf(providerEnum);
        boolean hasKey = notBlank(config.getApiKey());
        boolean configured = hasKey && notBlank(config.getBaseUrl()) && notBlank(config.getModel());
        boolean active = providerEnum.name().equalsIgnoreCase(activeProviderCode());
        String hint = configured ? null
                // 不带厂商名（V5.26 用户口径）：未配置就是"尚未配置AI模型"，用哪家厂商属于配置页的事
                : "尚未配置AI模型：请在【平台配置 → AI 模型配置】填写 Token 并选择模型（或点\"拉取模型列表\"）后保存";
        return new AiModelConfigVO(providerEnum.name(), providerEnum.getDisplayName(), config.getBaseUrl(),
                config.getModel(), hasKey, mask(config.getApiKey()), configured, active,
                config.getInputPrice(), config.getCacheInputPrice(), config.getOutputPrice(),
                config.getUpdatedAt(), hint);
    }

    @Override
    public void save(AiModelConfigRequest request) {
        if (request == null) {
            throw new BizException("配置内容不能为空");
        }
        AiProviderEnum provider = AiProviderEnum.of(request.provider());
        String baseUrl = notBlank(request.baseUrl()) ? request.baseUrl().trim() : provider.getDefaultBaseUrl();
        if (!notBlank(request.model())) {
            throw new BizException("请先选择模型（可点\"拉取模型列表\"，或手工填写模型名）");
        }
        validatePrices(request);
        AiModelConfig current = configOf(provider);
        String apiKey = resolveApiKey(request.apiKey(), current);
        if (!notBlank(apiKey)) {
            throw new BizException("请填写 " + provider.getDisplayName()
                    + " 的 API Token（各厂商的 Token 各存一份，之后切回该厂商无需重填）");
        }
        AiModelConfig row = new AiModelConfig();
        // 有该厂商的行就更新它，没有就新增：这是"不覆盖其它厂商"的关键
        row.setId(current.getId());
        row.setProvider(provider.name());
        row.setBaseUrl(baseUrl);
        row.setModel(request.model().trim());
        row.setApiKey(apiKey);
        row.setInputPrice(request.inputPrice());
        row.setCacheInputPrice(request.cacheInputPrice());
        row.setOutputPrice(request.outputPrice());
        row.setUpdatedAt(LocalDateTime.now());
        if (current.getId() == null) {
            configMapper.insert(row);
        } else {
            configMapper.updateById(row);
        }
        // 保存即生效（用户拍板口径）：把"当前启用厂商"指向它——所以在页面上"选中厂商 + 保存"就是切换厂商
        setActiveProvider(provider.name());
        clientFactory.invalidate();
        LOGGER.info("AI 模型配置已保存并生效：{} / {}", provider.getDisplayName(), row.getModel());
    }

    @Override
    public List<AiProviderVO> providers() {
        Map<String, AiModelConfig> saved = savedByProvider();
        String active = activeProviderCode();
        List<AiProviderVO> list = new ArrayList<>(AiProviderEnum.values().length);
        for (AiProviderEnum provider : AiProviderEnum.values()) {
            AiModelConfig config = saved.get(provider.name());
            String model = config == null || config.getModel() == null ? "" : config.getModel();
            boolean configured = config != null && notBlank(config.getApiKey()) && notBlank(model);
            list.add(new AiProviderVO(provider.name(), provider.getDisplayName(), provider.getDefaultBaseUrl(),
                    provider.freeModelList(), model, configured, provider.name().equalsIgnoreCase(active)));
        }
        return list;
    }

    @Override
    public List<AiModelOptionVO> models(String baseUrl, String apiKey, String provider) {
        AiProviderEnum providerEnum = AiProviderEnum.of(notBlank(provider) ? provider : activeProviderCode());
        AiModelConfig current = configOf(providerEnum);
        String url = trimTrailingSlash(notBlank(baseUrl) ? baseUrl : current.getBaseUrl());
        String key = resolveRequestKey(apiKey, providerEnum, current);
        String body;
        try {
            body = httpClient().get()
                    .uri(url + "/models")
                    .header("Authorization", "Bearer " + key)
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            throw new BizException("拉取模型列表失败：" + friendly(e)
                    + "（部分厂商不提供 /models 接口，可手工填写模型名）");
        }
        List<AiModelOptionVO> options = new ArrayList<>();
        try {
            JsonNode data = JsonUtils.mapper().readTree(body == null ? "{}" : body).path("data");
            if (data.isArray()) {
                for (JsonNode node : data) {
                    String id = node.path("id").asText("");
                    if (notBlank(id)) {
                        options.add(new AiModelOptionVO(id, providerEnum.isFreeModel(id)));
                    }
                }
            }
        } catch (Exception e) {
            throw new BizException("模型列表解析失败：" + e.getMessage());
        }
        // 厂商 /models 通常只列在售新模型，免费老档（如 glm-4-flash）可能不在返回里：
        // 把内置免费清单并入，保证"免费"标注能被看到（以厂商官网为准，清单可在 AiProviderEnum 调整）
        for (String freeModel : providerEnum.freeModelList()) {
            boolean exists = options.stream().anyMatch(option -> option.id().equalsIgnoreCase(freeModel));
            if (!exists) {
                options.add(new AiModelOptionVO(freeModel, true));
            }
        }
        if (options.isEmpty()) {
            throw new BizException("该厂商未返回模型列表（可手工填写模型名）");
        }
        options.sort(Comparator.comparing(AiModelOptionVO::id));
        return options;
    }

    @Override
    public String test(String baseUrl, String apiKey, String model) {
        AiModelConfig current = snapshot();
        AiModelConfig probe = new AiModelConfig();
        probe.setProvider(current.getProvider());
        probe.setBaseUrl(trimTrailingSlash(notBlank(baseUrl) ? baseUrl : current.getBaseUrl()));
        probe.setModel(notBlank(model) ? model : current.getModel());
        probe.setApiKey(resolveRequestKey(apiKey, AiProviderEnum.of(probe.getProvider()), current));
        if (!notBlank(probe.getModel())) {
            throw new BizException("请先选择或填写模型名再自检");
        }
        try {
            ChatClient client = clientFactory.build(probe);
            long startAt = System.currentTimeMillis();
            ChatResponse response = client.prompt()
                    .user(HEALTH_QUESTION)
                    // 自检是独立会话：不带记忆 Advisor 的 conversationId 会抛异常，故传临时 ID
                    .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, "ai-config-probe"))
                    .call()
                    .chatResponse();
            long cost = System.currentTimeMillis() - startAt;
            String reply = response.getResult() == null || response.getResult().getOutput() == null
                    ? null : response.getResult().getOutput().getText();
            // 自检真实消耗 token，故也记一行用量（与 /api/ai/health 同口径：不受额度拦截，但计入当日用量）
            recordProbeUsage(probe, response, startAt);
            // 只回执"是否响应 + 耗时"，不展示回复正文：自检提示词会被系统提示词判定为越界问题，
            // 模型可能回一句拒答话术，展示出来反而让人误以为配置有误
            LOGGER.info("模型连通性自检通过：{} 耗时 {}ms，回复长度 {}", probe.getModel(), cost,
                    reply == null ? 0 : reply.length());
            return "连通正常（" + probe.getModel() + " 已响应，耗时 " + cost + "ms）";
        } catch (Exception e) {
            throw new BizException("连通失败：" + friendly(e));
        }
    }

    /**
     * 记一行"自检"用量流水（单价按**被探测厂商**的那一行取，故带上 provider 覆盖）。
     *
     * <p>只取上游返回的真实 usage，不做事后估算——自检只是配置流程里的一次探测；
     * 无 usage 时记为 0，界面照实展示。
     *
     * @param probe    本次探测使用的配置（模型与厂商按被探测的那家记录）
     * @param response 上游响应（可含 usage）
     * @param startAt  起始毫秒
     */
    private void recordProbeUsage(AiModelConfig probe, ChatResponse response, long startAt) {
        Usage usage = response == null || response.getMetadata() == null
                ? null : response.getMetadata().getUsage();
        AiUsageRecord record = new AiUsageRecord();
        record.setBiz(AiUsageBizEnum.HEALTH.name());
        record.setProvider(probe.getProvider());
        record.setModel(probe.getModel());
        record.setQuestionChars(HEALTH_QUESTION.length());
        record.setDurationMs((int) (System.currentTimeMillis() - startAt));
        if (usage != null && usage.getTotalTokens() != null) {
            record.setPromptTokens(usage.getPromptTokens() == null ? 0 : usage.getPromptTokens());
            record.setCompletionTokens(usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens());
            record.setCachedTokens(usage.getCacheReadInputTokens() == null ? 0 : usage.getCacheReadInputTokens().intValue());
        }
        usageService.record(record);
    }

    /**
     * 取某厂商的配置行。
     *
     * @param provider    厂商代码
     * @param withDefaults true = 该厂商还没有行时返回带默认端点的空壳（供页面首次填写）；
     *                     false = 直接返回 null（供"是否已有配置"的判断）
     */
    private AiModelConfig configOf(String provider, boolean withDefaults) {
        if (!notBlank(provider)) {
            return withDefaults ? emptyConfig(AiProviderEnum.ZHIPU) : null;
        }
        AiModelConfig config = configMapper.selectOne(new LambdaQueryWrapper<AiModelConfig>()
                .eq(AiModelConfig::getProvider, provider.trim()));
        if (config != null) {
            return config;
        }
        return withDefaults ? emptyConfig(AiProviderEnum.of(provider)) : null;
    }

    /** 取某厂商的配置行（没有则返回带默认端点的空壳） */
    private AiModelConfig configOf(AiProviderEnum provider) {
        return configOf(provider.name(), true);
    }

    /** 该厂商的空壳配置（默认端点 + 空模型/Token，单价 0） */
    private AiModelConfig emptyConfig(AiProviderEnum provider) {
        AiModelConfig empty = new AiModelConfig();
        empty.setProvider(provider.name());
        empty.setBaseUrl(provider.getDefaultBaseUrl());
        empty.setModel("");
        empty.setApiKey("");
        empty.setInputPrice(BigDecimal.ZERO);
        empty.setCacheInputPrice(BigDecimal.ZERO);
        empty.setOutputPrice(BigDecimal.ZERO);
        return empty;
    }

    /** 全部已保存的厂商配置（provider 代码 → 行），用于一次性渲染厂商清单 */
    private Map<String, AiModelConfig> savedByProvider() {
        Map<String, AiModelConfig> map = new HashMap<>(AiProviderEnum.values().length);
        for (AiModelConfig config : configMapper.selectList(null)) {
            map.put(config.getProvider(), config);
        }
        return map;
    }

    /** 第一个已配 Token 的厂商配置（用于"当前厂商缺失"时的自愈） */
    private AiModelConfig firstConfigured() {
        return configMapper.selectList(new LambdaQueryWrapper<AiModelConfig>()
                        .orderByAsc(AiModelConfig::getId))
                .stream()
                .filter(config -> notBlank(config.getApiKey()))
                .findFirst()
                .orElse(null);
    }

    /** 当前启用的厂商代码（未设置返回 null） */
    private String activeProviderCode() {
        AiRuntimeConfig runtime = runtimeMapper.selectById(RUNTIME_ID);
        return runtime == null ? null : runtime.getActiveProvider();
    }

    /**
     * 设置当前启用的厂商（保存配置后调用；单行表缺行时补建）。
     *
     * <p>只更新 active_provider 字段，额度三列原样保留（MyBatis-Plus 跳过 null 字段）。
     *
     * @param provider 厂商代码
     */
    private void setActiveProvider(String provider) {
        AiRuntimeConfig runtime = runtimeMapper.selectById(RUNTIME_ID);
        if (runtime == null) {
            runtime = new AiRuntimeConfig();
            runtime.setId(RUNTIME_ID);
            runtime.setActiveProvider(provider);
            runtimeMapper.insert(runtime);
            return;
        }
        runtime.setActiveProvider(provider);
        runtimeMapper.updateById(runtime);
    }

    /**
     * 解析"本次请求要用哪个 Token"：请求里带了明文就用它，否则用**该厂商自己**已存的 Token。
     *
     * @param requestKey 请求里传入的 Token（可为空或打码值）
     * @param provider   厂商
     * @param current    该厂商的配置（空壳表示还没配过）
     */
    private String resolveRequestKey(String requestKey, AiProviderEnum provider, AiModelConfig current) {
        if (notBlank(requestKey) && !isMasked(requestKey.trim())) {
            return requestKey.trim();
        }
        if (notBlank(current.getApiKey())) {
            return current.getApiKey();
        }
        throw new BizException("请先填写 " + provider.getDisplayName() + " 的 API Token");
    }

    /**
     * 解析要写入的 Token：请求为空或等于该厂商打码值 → 保留其已存值（避免打码值覆盖真实 Key）。
     */
    private String resolveApiKey(String requestKey, AiModelConfig current) {
        String existing = current == null ? null : current.getApiKey();
        if (!notBlank(requestKey) || isMasked(requestKey.trim())) {
            return existing;
        }
        return requestKey.trim();
    }

    /** 单价校验：只允许非负数（元/百万 token；null 表示"保持原值"） */
    private void validatePrices(AiModelConfigRequest request) {
        checkPrice(request.inputPrice(), "输入单价");
        checkPrice(request.cacheInputPrice(), "缓存命中输入单价");
        checkPrice(request.outputPrice(), "输出单价");
    }

    /**
     * 单价校验：只允许非负数。
     *
     * @param price 单价（元/百万 token，可为 null 表示保持原值）
     * @param name  字段中文名（用于报错文案）
     */
    private void checkPrice(BigDecimal price, String name) {
        if (price != null && price.signum() < 0) {
            throw new BizException(name + "不能为负数（单位：元/百万 token，填 0 表示不计算费用）");
        }
    }

    /** 是否是打码值（含 * 号，且不是真实 Token 的常见形态） */
    private boolean isMasked(String value) {
        return value != null && value.contains("*");
    }

    /** Token 打码：保留尾部若干位便于核对 */
    private String mask(String apiKey) {
        if (!notBlank(apiKey)) {
            return null;
        }
        String key = apiKey.trim();
        if (key.length() <= MASK_KEEP_TAIL) {
            return "****";
        }
        return "****" + key.substring(key.length() - MASK_KEEP_TAIL);
    }

    /** 供模型列表/自检使用的 HTTP 客户端（带超时，避免挂死） */
    private RestClient httpClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS));
        factory.setReadTimeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS));
        return RestClient.builder().requestFactory(factory).build();
    }

    /** 异常转友好文案：把常见的 401/404/超时讲清楚 */
    private String friendly(Exception e) {
        String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        if (message.contains("401") || message.toLowerCase().contains("unauthorized")) {
            return "Token 无效或已过期（401）";
        }
        if (message.contains("404")) {
            return "端点地址不对（404），请核对 Base URL 是否需要带 /v1 或 /v4 后缀";
        }
        if (message.contains("timeout") || message.contains("timed out")) {
            return "请求超时，请检查网络或 Base URL";
        }
        return message.length() > 200 ? message.substring(0, 200) : message;
    }

    private String trimTrailingSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
