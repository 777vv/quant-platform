package com.quant.ai.service.impl;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.quant.ai.config.AiConfig.AiChatClientFactory;
import com.quant.ai.config.AiProperties;
import com.quant.ai.entity.AiModelConfig;
import com.quant.ai.service.AiModelConfigService;
import com.quant.ai.dto.AiChatRequest;
import com.quant.ai.dto.AiConfigVO;
import com.quant.ai.dto.AiMessageVO;
import com.quant.ai.dto.AiModelConfigVO;
import com.quant.ai.dto.AiSessionVO;
import com.quant.ai.dto.AiUsageRecord;
import com.quant.ai.entity.AiChatMessage;
import com.quant.ai.entity.AiChatSession;
import com.quant.ai.enums.AiUsageBizEnum;
import com.quant.ai.mapper.AiChatMessageMapper;
import com.quant.ai.mapper.AiChatSessionMapper;
import com.quant.ai.memory.RedisChatMemoryRepository;
import com.quant.ai.prompt.PromptGuard;
import com.quant.ai.prompt.SystemPrompts;
import com.quant.ai.service.AiChatService;
import com.quant.ai.service.AiUsageService;
import cn.dev33.satoken.stp.StpUtil;

import com.quant.common.exception.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;

/**
 * AI 助手服务实现（FR6）。
 * 事件帧约定（M5-05，前端按 event 名分发）：
 * - session：会话建立，data 含 sessionId 与 model；
 * - tool   ：模型决定调用工具，data 含工具名与参数（前端展示"正在查询…"过程提示）；
 * - token  ：回答增量文本，前端按序拼接实现打字机效果；
 * - error  ：流中异常，data 含中文原因；
 * - done   ：回答结束，data 含会话 ID、总字数与本次用量（V3.9）。
 * 落库时机：提问在发起前落库；回答在流正常结束后把拼接结果落库（中途异常不落半截回答）。
 *
 * <p>用量统计（V3.9）：每次咨询记一行 {@code ai_usage_log}（**一次咨询一行**，工具调用的多轮
 * token 已由 {@link TokenUsageAccumulator} 累加）；额度校验在**调用模型之前**完成，
 * 超限直接抛业务异常、不发上游请求——拒绝才是真的省钱。
 */
@Service
public class AiChatServiceImpl implements AiChatService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AiChatServiceImpl.class);

    /** 会话标题最大长度（侧栏展示用） */
    private static final int TITLE_MAX_LEN = 30;

    /** 事件名常量 */
    private static final String EVENT_SESSION = "session";

    private static final String EVENT_TOOL = "tool";

    private static final String EVENT_TOKEN = "token";

    private static final String EVENT_ERROR = "error";

    private static final String EVENT_DONE = "done";

    /** 角色常量（与 ai_chat_message.role 取值一致） */
    private static final String ROLE_USER = "user";

    private static final String ROLE_ASSISTANT = "assistant";

    /** 连通性自检使用的临时会话 ID（用后即清，不进入用户会话列表） */
    private static final String HEALTH_CONVERSATION_ID = "ai-health-check";

    /** 连通性自检的固定提问（同时作为用量统计的输入字符基准） */
    private static final String HEALTH_QUESTION = "请回复两个字：正常";

    /** 模型配置（厂商/端点/模型/Token 的运行时来源）与客户端工厂 */
    private final AiModelConfigService configService;

    private final AiChatClientFactory clientFactory;

    private final ChatMemory chatMemory;

    private final AiProperties properties;

    private final AiChatSessionMapper sessionMapper;

    private final AiChatMessageMapper messageMapper;

    private final RedisChatMemoryRepository memoryRepository;

    /** 用量与费用服务（记账 + 额度校验） */
    private final AiUsageService usageService;

    /**
     * 取当前生效的对话客户端：配置来自数据库（平台配置维护），配置不完整时给出可操作提示。
     * 客户端按配置指纹缓存在工厂里，配置保存后自动重建，无需重启应用。
     */
    private ChatClient client() {
        AiModelConfig config = configService.snapshot();
        if (config.getApiKey() == null || config.getApiKey().isBlank()
                || config.getBaseUrl() == null || config.getBaseUrl().isBlank()
                || config.getModel() == null || config.getModel().isBlank()) {
            throw new BizException("尚未配置模型：请在【平台配置 → AI 模型配置】选择厂商、填写 Token 与模型后保存");
        }
        return clientFactory.of(config);
    }

    /** 当前模型名（用于前端展示与事件帧；未配置返回空串） */
    private String currentModelName() {
        String model = configService.snapshot().getModel();
        return model == null ? "" : model;
    }

    public AiChatServiceImpl(AiModelConfigService configService, AiChatClientFactory clientFactory,
                             ChatMemory chatMemory, AiProperties properties,
                             AiChatSessionMapper sessionMapper, AiChatMessageMapper messageMapper,
                             RedisChatMemoryRepository memoryRepository, AiUsageService usageService) {
        this.configService = configService;
        this.clientFactory = clientFactory;
        this.chatMemory = chatMemory;
        this.properties = properties;
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.memoryRepository = memoryRepository;
        this.usageService = usageService;
    }

    @Override
    public Flux<ServerSentEvent<String>> stream(AiChatRequest request) {
        // 用 defer 把校验与建会话推迟到订阅时执行：本接口 produces=text/event-stream，
        // 若在方法体内直接抛异常，异常处理器无法用 JSON 写回（内容协商已被固定为事件流），
        // 因此统一转为 error 事件帧下发，前端按事件类型展示即可。
        return Flux.defer(() -> {
            String question = validate(request);
            String sessionId = prepareSession(request.getSessionId(), question);
            if (PromptGuard.isJailbreak(question)) {
                return refusalStream(sessionId, question);
            }
            // 额度校验放在护栏之后、调模型之前：护栏零成本先拦；超限时连模型都不调
            usageService.checkQuota();
            StringBuilder answer = new StringBuilder();
            TokenUsageAccumulator tokens = new TokenUsageAccumulator();
            AiUsageRecord usage = newUsage(AiUsageBizEnum.CHAT.name(), sessionId, question);
            long startAt = System.currentTimeMillis();
            return Flux.just(event(EVENT_SESSION, Map.of("sessionId", sessionId, "model", nullSafe(currentModelName()))))
                    .concatWith(client().prompt()
                            .user(question)
                            .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, sessionId))
                            .stream()
                            .chatResponse()
                            .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                            .doOnNext(response -> accumulate(answer, tokens, response))
                            .flatMap(this::toEvents)
                            .doOnComplete(() -> finishChat(usage, tokens, question, answer.toString(), startAt))
                            .doOnError(throwable -> recordFailure(usage, tokens, answer.length(), startAt, throwable))
                            .concatWith(Flux.defer(() -> Flux.just(event(EVENT_DONE,
                                    doneData(sessionId, answer.length(), usage))))));
        }).onErrorResume(this::toErrorEvent);
    }

    @Override
    public String chatSync(AiChatRequest request) {
        String question = validate(request);
        String sessionId = prepareSession(request.getSessionId(), question);
        if (PromptGuard.isJailbreak(question)) {
            persistAnswer(sessionId, PromptGuard.REFUSAL);
            recordGuard(sessionId, question);
            return PromptGuard.REFUSAL;
        }
        usageService.checkQuota();
        AiUsageRecord usage = newUsage(AiUsageBizEnum.CHAT.name(), sessionId, question);
        TokenUsageAccumulator tokens = new TokenUsageAccumulator();
        long startAt = System.currentTimeMillis();
        ChatResponse response;
        try {
            // 降级路径（技术文档 6.8 的 stream=false 语义）：非流式只能拿到**最后一轮**的 usage，
            // 工具调用的中间轮不透出，故触发工具时本路径 token 可能偏低；前端正常使用走流式路径
            response = client().prompt()
                    .user(question)
                    .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, sessionId))
                    .call()
                    .chatResponse();
        } catch (RuntimeException e) {
            recordFailure(usage, tokens, 0, startAt, e);
            throw e;
        }
        tokens.accept(response);
        String answer = nullSafe(textOf(response));
        finishChat(usage, tokens, question, answer, startAt);
        return answer;
    }

    /**
     * 命中越狱护栏时的响应：不调用大模型，直接以统一话术回复并落库。
     * 以普通回答（而非错误）呈现，用户侧体验与模型自身的拒答一致。
     */
    private Flux<ServerSentEvent<String>> refusalStream(String sessionId, String question) {
        persistAnswer(sessionId, PromptGuard.REFUSAL);
        AiUsageRecord usage = recordGuard(sessionId, question);
        LOGGER.info("命中提示词护栏，已直接拒答（长度 {} 字）", question.length());
        return Flux.just(
                event(EVENT_SESSION, Map.of("sessionId", sessionId, "model", nullSafe(currentModelName()))),
                event(EVENT_TOKEN, Map.of("text", PromptGuard.REFUSAL)),
                event(EVENT_DONE, doneData(sessionId, PromptGuard.REFUSAL.length(), usage)));
    }

    @Override
    public String health() {
        ensureUsable();
        AiUsageRecord usage = newUsage(AiUsageBizEnum.HEALTH.name(), null, HEALTH_QUESTION);
        TokenUsageAccumulator tokens = new TokenUsageAccumulator();
        long startAt = System.currentTimeMillis();
        ChatResponse response = client().prompt()
                .user(HEALTH_QUESTION)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, HEALTH_CONVERSATION_ID))
                .call()
                .chatResponse();
        // 自检不进入会话历史：清掉临时记忆，避免污染用户的会话上下文
        chatMemory.clear(HEALTH_CONVERSATION_ID);
        tokens.accept(response);
        String reply = nullSafe(textOf(response));
        // 自检真实消耗 token，故也记一行用量（但**不受额度拦截**：否则配好 Key 反而无法自检）
        finishChat(usage, tokens, HEALTH_QUESTION, reply, startAt);
        return reply;
    }

    @Override
    public List<AiSessionVO> sessions() {
        // V6.21 会话按账号隔离：列表只返回当前登录用户自己的会话
        return sessionMapper.selectList(new LambdaQueryWrapper<AiChatSession>()
                        .eq(AiChatSession::getUserId, currentUserId())
                        .orderByDesc(AiChatSession::getUpdatedAt).orderByDesc(AiChatSession::getId))
                .stream()
                .map(row -> new AiSessionVO(row.getSessionId(), row.getTitle(), row.getUpdatedAt()))
                .toList();
    }

    @Override
    public List<AiMessageVO> messages(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new BizException("会话 ID 不能为空");
        }
        // V6.21 会话按账号隔离：不是自己的会话一律当作"没有消息"（不泄露存在性，也不回别人内容）
        if (!ownedByCurrentUser(sessionId)) {
            return List.of();
        }
        return messageMapper.selectList(new LambdaQueryWrapper<AiChatMessage>()
                        .eq(AiChatMessage::getSessionId, sessionId)
                        .orderByAsc(AiChatMessage::getId))
                .stream()
                .map(row -> new AiMessageVO(row.getRole(), row.getContent(), row.getCreatedAt()))
                .toList();
    }

    @Override
    public void deleteSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new BizException("会话 ID 不能为空");
        }
        // V6.21 会话按账号隔离：只能删自己的会话（别人的会话静默跳过，不泄露存在性）
        if (!ownedByCurrentUser(sessionId)) {
            return;
        }
        messageMapper.delete(new LambdaQueryWrapper<AiChatMessage>().eq(AiChatMessage::getSessionId, sessionId));
        sessionMapper.delete(new LambdaQueryWrapper<AiChatSession>().eq(AiChatSession::getSessionId, sessionId));
        memoryRepository.deleteByConversationId(sessionId);
    }

    @Override
    public AiConfigVO config() {
        // 不传厂商 = 看当前启用的那家（V4.0：配置按厂商分行）
        AiModelConfigVO modelConfig = configService.view(null);
        boolean configured = modelConfig.configured();
        Map<String, String> extras = new java.util.HashMap<>();
        if (modelConfig.hint() != null) {
            extras.put("hint", modelConfig.hint());
        }
        if (!properties.isEnabled()) {
            extras.put("hint", "AI 助手已在配置中关闭（quant.ai.enabled=false）");
        }
        extras.put("provider", modelConfig.providerName());
        return new AiConfigVO(properties.isEnabled(), configured, nullSafe(modelConfig.model()),
                properties.getMemoryWindow(), extras);
    }

    /** 入参校验：开关、Key、提问长度 */
    private String validate(AiChatRequest request) {
        ensureUsable();
        if (request == null || request.getQuestion() == null || request.getQuestion().isBlank()) {
            throw new BizException("请输入问题内容");
        }
        String question = request.getQuestion().trim();
        if (question.length() > properties.getMaxQuestionLength()) {
            throw new BizException("问题过长，请控制在 " + properties.getMaxQuestionLength() + " 字以内");
        }
        return question;
    }

    /** 可用性检查：开关开启且 Key 已配置 */
    private void ensureUsable() {
        if (!properties.isEnabled()) {
            throw new BizException("AI 助手已关闭（quant.ai.enabled=false）");
        }
        // 模型配置来自数据库：未配置时给出去哪里配的指引（不再提配置文件）
        AiModelConfig config = configService.snapshot();
        if (config.getApiKey() == null || config.getApiKey().isBlank()
                || config.getModel() == null || config.getModel().isBlank()) {
            throw new BizException("尚未配置模型：请在【平台配置 → AI 模型配置】选择厂商、填写 Token 与模型后保存");
        }
    }

    /**
     * 准备会话：无会话 ID 则新建（标题取提问摘要），并把本轮提问落入 MySQL 全量历史。
     */
    private String prepareSession(String sessionId, String question) {
        long userId = currentUserId();
        String id = sessionId == null || sessionId.isBlank()
                ? UUID.randomUUID().toString().replace("-", "") : sessionId;
        AiChatSession session = sessionMapper.selectOne(new LambdaQueryWrapper<AiChatSession>()
                .eq(AiChatSession::getSessionId, id));
        // V6.21 会话按账号隔离：会话 ID 存在但属于别人（旧本地缓存、手工构造）时另起一个新会话，
        // 避免把消息写进别人的历史；memory 的 conversationId 也随之换成新 ID。
        if (session != null && !userIdEquals(session.getUserId(), userId)) {
            id = UUID.randomUUID().toString().replace("-", "");
            session = null;
        }
        if (session == null) {
            session = new AiChatSession();
            session.setSessionId(id);
            session.setUserId(userId);
            session.setTitle(question.length() <= TITLE_MAX_LEN ? question
                    : question.substring(0, TITLE_MAX_LEN) + "…");
            session.setUpdatedAt(LocalDateTime.now());
            sessionMapper.insert(session);
        } else {
            session.setUpdatedAt(LocalDateTime.now());
            sessionMapper.updateById(session);
        }
        insertMessage(id, ROLE_USER, question);
        return id;
    }

    /** 当前登录用户 ID（会话归属判定的唯一来源） */
    private long currentUserId() {
        return StpUtil.getLoginIdAsLong();
    }

    /** 会话是否属于当前登录用户（会话不存在也算"不是自己的"） */
    private boolean ownedByCurrentUser(String sessionId) {
        AiChatSession session = sessionMapper.selectOne(new LambdaQueryWrapper<AiChatSession>()
                .eq(AiChatSession::getSessionId, sessionId));
        return session != null && userIdEquals(session.getUserId(), currentUserId());
    }

    /** 归属比较：库里未迁移的老值（null/0）不算任何人的 */
    private boolean userIdEquals(Long sessionUserId, long currentUserId) {
        return sessionUserId != null && sessionUserId == currentUserId;
    }

    /** 回答落库（空回答不落，避免污染历史） */
    private void persistAnswer(String sessionId, String answer) {
        if (answer != null && !answer.isBlank()) {
            insertMessage(sessionId, ROLE_ASSISTANT, answer);
        }
    }

    private void insertMessage(String sessionId, String role, String content) {
        AiChatMessage message = new AiChatMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        messageMapper.insert(message);
    }

    /** 累积回答文本（供落库）并收下 token usage（供费用统计） */
    private void accumulate(StringBuilder answer, TokenUsageAccumulator tokens, ChatResponse response) {
        tokens.accept(response);
        String text = textOf(response);
        if (text != null && !text.isEmpty()) {
            answer.append(text);
        }
    }

    /** 单帧响应 → 零或多个事件（工具调用帧与文本帧可能同帧） */
    private Flux<ServerSentEvent<String>> toEvents(ChatResponse response) {
        List<ServerSentEvent<String>> events = new ArrayList<>(2);
        if (response.hasToolCalls() && response.getResult() != null && response.getResult().getOutput() != null) {
            for (AssistantMessage.ToolCall call : response.getResult().getOutput().getToolCalls()) {
                events.add(event(EVENT_TOOL, Map.of("name", nullSafe(call.name()),
                        "arguments", nullSafe(call.arguments()))));
            }
        }
        String text = textOf(response);
        if (text != null && !text.isEmpty()) {
            events.add(event(EVENT_TOKEN, Map.of("text", text)));
        }
        return Flux.fromIterable(events);
    }

    /** 取单帧文本（结果或输出缺失时返回 null） */
    private String textOf(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return null;
        }
        return response.getResult().getOutput().getText();
    }

    /** 异常 → error 事件（业务异常原样展示，其余给中文概述并保留简短原因便于排查） */
    private Flux<ServerSentEvent<String>> toErrorEvent(Throwable throwable) {
        LOGGER.warn("AI 对话流异常: {}", throwable.getMessage());
        String message;
        if (throwable instanceof BizException) {
            message = throwable.getMessage();
        } else {
            String reason = throwable.getMessage() == null
                    ? throwable.getClass().getSimpleName() : throwable.getMessage();
            message = "AI 服务暂时不可用，请稍后重试（" + reason + "）";
        }
        return Flux.just(event(EVENT_ERROR, Map.of("message", message)));
    }

    /** 构造用量记录骨架（用途/会话/提问字数；模型与厂商由用量服务按当前配置补齐） */
    private AiUsageRecord newUsage(String biz, String sessionId, String question) {
        AiUsageRecord usage = new AiUsageRecord();
        usage.setBiz(biz);
        usage.setSessionId(sessionId);
        usage.setQuestionChars(question == null ? 0 : question.length());
        return usage;
    }

    /**
     * 记一行"护栏拒答"用量：未调模型，token 与费用恒为 0。
     *
     * <p>之所以也记：这样"今日问了几次"与"实际计费几次"能对上，拦截率也看得见。
     *
     * @param sessionId 会话 ID
     * @param question  提问原文（只记字数）
     * @return 用量记录（供结束帧展示，其 cost 已被用量服务回填为 0）
     */
    private AiUsageRecord recordGuard(String sessionId, String question) {
        AiUsageRecord usage = newUsage(AiUsageBizEnum.GUARD.name(), sessionId, question);
        usage.setAnswerChars(PromptGuard.REFUSAL.length());
        usageService.record(usage);
        return usage;
    }

    /**
     * 成功收尾：回答落库（有会话时）+ 记一行用量流水。
     *
     * <p>token 优先取上游真实 usage；一帧都没回时按字符估算兜底并置 {@code estimated}
     * （输入按「提问 + 系统提示词」估，不含记忆窗口历史，故通常偏低；1 字≈1token 取高，宁高勿低）。
     *
     * @param usage    用量记录（其 cost 会被用量服务回填，供结束帧展示）
     * @param tokens   本次咨询的 token 累加器
     * @param question 提问原文（估算用）
     * @param answer   回答全文（落库与字数统计用）
     * @param startAt  起始毫秒（算耗时）
     */
    private void finishChat(AiUsageRecord usage, TokenUsageAccumulator tokens,
                            String question, String answer, long startAt) {
        if (usage.getSessionId() != null) {
            persistAnswer(usage.getSessionId(), answer);
        }
        if (!tokens.estimated() && tokens.totalTokens() > 0) {
            applyTokens(tokens, usage);
        } else {
            int inputChars = usage.getQuestionChars() + SystemPrompts.SYSTEM_PROMPT.length();
            tokens.estimateFromChars(inputChars, answer.length());
            applyTokens(tokens, usage);
            usage.setEstimated(true);
            LOGGER.warn("上游未回传 usage，本次用量按字符估算（输入 {} 字 / 输出 {} 字）", inputChars, answer.length());
        }
        usage.setAnswerChars(answer.length());
        usage.setDurationMs(durationOf(startAt));
        usage.setStatus(1);
        usageService.record(usage);
    }

    /**
     * 失败也记一行：上游报错时若静默少一条，用量看板就对不上账。
     *
     * <p>token 只写**已收到的真实 usage**（中途失败时上游到底消耗了多少无从得知，不编数字）。
     */
    private void recordFailure(AiUsageRecord usage, TokenUsageAccumulator tokens,
                               int answerChars, long startAt, Throwable throwable) {
        if (tokens.totalTokens() > 0) {
            applyTokens(tokens, usage);
        }
        usage.setAnswerChars(Math.max(answerChars, 0));
        usage.setDurationMs(durationOf(startAt));
        usage.setStatus(0);
        usage.setErrorMsg(errorText(throwable));
        usageService.record(usage);
    }

    /** 把累加结果（轮数 + 输入/缓存命中/输出 token）写进用量记录 */
    private void applyTokens(TokenUsageAccumulator tokens, AiUsageRecord usage) {
        usage.setRounds(tokens.rounds());
        usage.setPromptTokens(tokens.promptTokens());
        usage.setCachedTokens(tokens.cachedTokens());
        usage.setCompletionTokens(tokens.completionTokens());
    }

    /** 耗时（毫秒，封顶 int 上限以免溢出） */
    private int durationOf(long startAt) {
        return (int) Math.min(System.currentTimeMillis() - startAt, Integer.MAX_VALUE);
    }

    /** 失败原因文案（业务异常原样，其余带简短原因便于排查） */
    private String errorText(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        if (throwable instanceof BizException) {
            return throwable.getMessage();
        }
        String reason = throwable.getMessage() == null
                ? throwable.getClass().getSimpleName() : throwable.getMessage();
        return "AI 服务异常：" + reason;
    }

    /** 结束帧数据：会话 ID + 字数 + 本次用量（前端据此在回答下方显示"本次 N token ≈ ¥X"） */
    private Map<String, Object> doneData(String sessionId, int chars, AiUsageRecord usage) {
        Map<String, Object> data = new LinkedHashMap<>(4);
        data.put("sessionId", sessionId);
        data.put("chars", String.valueOf(chars));
        data.put("usage", usageData(usage));
        return data;
    }

    /** 本次用量数据（值统一转字符串：与其余事件帧一致，前端按需 Number 化） */
    private Map<String, Object> usageData(AiUsageRecord usage) {
        Map<String, Object> data = new LinkedHashMap<>(8);
        data.put("rounds", String.valueOf(usage.getRounds()));
        data.put("promptTokens", String.valueOf(usage.getPromptTokens()));
        data.put("cachedTokens", String.valueOf(usage.getCachedTokens()));
        data.put("completionTokens", String.valueOf(usage.getCompletionTokens()));
        data.put("totalTokens", String.valueOf(usage.getPromptTokens() + usage.getCompletionTokens()));
        data.put("estimated", String.valueOf(usage.isEstimated()));
        // 费用由用量服务按当前单价回填；为空（写库失败）回空串，前端显示 "--"
        data.put("cost", costText(usage.getCost()));
        return data;
    }

    /**
     * 费用文案：去掉多余的尾零（0.187500 → 0.1875、0.000000 → 0）。
     *
     * <p>注意 BigDecimal 的坑：`new BigDecimal("0.000000").stripTrailingZeros().toPlainString()` 仍是
     * "0.000000"（内部变成 0E-6），所以零值单独返回 "0"。
     *
     * @param cost 费用（可为 null）
     */
    private String costText(BigDecimal cost) {
        if (cost == null) {
            return "";
        }
        BigDecimal trimmed = cost.stripTrailingZeros();
        return trimmed.signum() == 0 ? "0" : trimmed.toPlainString();
    }

    /** 构造 SSE 事件帧（数据统一 JSON 序列化，避免换行/引号破坏帧结构） */
    private ServerSentEvent<String> event(String name, Map<String, ?> data) {
        return ServerSentEvent.<String>builder()
                .event(name)
                .data(com.quant.common.util.JsonUtils.toJson(data))
                .build();
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
