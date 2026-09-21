package com.quant.ai.config;

import com.quant.ai.entity.AiModelConfig;
import com.quant.ai.memory.RedisChatMemoryRepository;
import com.quant.ai.prompt.SystemPrompts;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 装配：会话记忆 + **运行时可重建的 ChatClient 工厂**。
 *
 * <p>背景（V3.0 变更）：模型厂商 / Base URL / 模型名 / Token 原先来自 `spring.ai.openai.*`，
 * 由 Spring AI 自动装配在启动时固化；现改为在【平台配置 → AI 模型配置】里维护并存库，
 * 因此关闭了 Spring AI 的 chat 自动装配（`spring.ai.model.chat=none`），
 * 由 {@link AiChatClientFactory} 用 `OpenAiChatOptions.baseUrl/apiKey` 在运行时构建客户端——
 * 配置一保存即失效重建，无需重启应用。
 */
@Configuration
public class AiConfig {

    /**
     * 会话记忆：窗口由配置控制（默认 20 条），底层走 RedisChatMemoryRepository。
     */
    @Bean
    public ChatMemory chatMemory(RedisChatMemoryRepository repository, AiProperties properties) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(properties.getMemoryWindow())
                .build();
    }

    /**
     * 工具调用限额管理器（V4.5）：不用 Spring AI 默认值（单工具 40 次 / 合计 150 次，过于宽松），
     * 由平台在 application.yml 显式配置（quant.ai.tool-limit.*）。
     *
     * <p>三层防线的分工：本 Bean 管"一次对话内工具循环的轮数"；每日 token/费用额度管"总量"；
     * 流式超时管"卡死"。容器里存在本 Bean 后，{@code ChatClient.builder(chatModel)} 会自动拾取
     * （默认的 ToolCallingAdvisor 用它执行工具循环），无需在对话代码里做任何改动。
     *
     * <p>外面包一层 {@link LoggingToolCallingManager}：把每次工具调用的"名称 + 入参 + 结果摘要"
     * 以 `AI-TOOL` 前缀打进日志文件（logger: com.quant.ai.tool），便于排查"这轮对话调了哪些工具"。
     */
    @Bean
    public ToolCallingManager toolCallingManager(AiProperties properties) {
        AiProperties.ToolLimit limit = properties.getToolLimit();
        DefaultToolCallingManager.Builder builder = DefaultToolCallingManager.builder();
        if (limit.getMaxCallsPerTool() > 0) {
            builder.maxCallsPerTool(limit.getMaxCallsPerTool());
        } else {
            builder.unlimitedCallsPerTool();
        }
        if (limit.getMaxTotalToolCalls() > 0) {
            builder.maxTotalToolCalls(limit.getMaxTotalToolCalls());
        } else {
            builder.unlimitedTotalToolCalls();
        }
        return new LoggingToolCallingManager(builder.onLimitExceeded(limit.behavior()).build());
    }

    /**
     * 对话客户端工厂：按数据库里的当前配置构建 ChatClient（系统提示词 + 七工具 + 记忆 Advisor）。
     *
     * <p>⚠️ 契约：记忆 Advisor 作为默认 Advisor 后，**每次请求都必须携带会话 ID**
     * （`advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, sessionId))`），否则 Advisor 会抛
     * `IllegalArgumentException: conversationId cannot be null`；不参与会话的一次性调用（如连通性自检）
     * 也应传入临时会话 ID，用后清理。
     */
    @Bean
    public AiChatClientFactory chatClientFactory(ChatMemory chatMemory, AiProperties properties,
                                                AiToolRegistry toolRegistry, ObjectProvider<ToolCallingManager> managerProvider) {
        return new AiChatClientFactory(chatMemory, properties, toolRegistry, managerProvider);
    }

    /**
     * 运行时 ChatClient 工厂：缓存当前配置对应的客户端，配置变更时失效重建。
     */
    public static class AiChatClientFactory {

        private final ChatMemory chatMemory;

        private final AiProperties properties;

        private final AiToolRegistry toolRegistry;

        /** 容器里的工具调用管理器（携带平台显式配置的限额）；为空时退回框架默认 */
        private final ObjectProvider<ToolCallingManager> managerProvider;

        /** 缓存的客户端与其对应的配置指纹（指纹变化即重建） */
        private volatile ChatClient cachedClient;

        private volatile String cachedFingerprint;

        AiChatClientFactory(ChatMemory chatMemory, AiProperties properties, AiToolRegistry toolRegistry,
                            ObjectProvider<ToolCallingManager> managerProvider) {
            this.chatMemory = chatMemory;
            this.properties = properties;
            this.toolRegistry = toolRegistry;
            this.managerProvider = managerProvider;
        }

        /**
         * 用指定配置构建 ChatClient（不缓存，供连通性自检等一次性调用）。
         *
         * @param config 配置快照
         */
        public ChatClient build(AiModelConfig config) {
            OpenAiChatOptions options = OpenAiChatOptions.builder()
                    .baseUrl(config.getBaseUrl())
                    .apiKey(config.getApiKey())
                    .model(config.getModel())
                    .temperature(properties.getTemperature())
                    // 流式也要回传 usage（V3.9）：不带这个参数时多数 OpenAI 兼容厂商只在非流式返回 usage，
                    // 用量统计就只能退化成字符估算（实测 DEEPSEEK 带上后每轮恰回 1 帧 usage）
                    .streamUsage(true)
                    .build();
            // 注意 Spring AI 2.0.1 的 Builder 方法名是 options(...)，不是 defaultOptions(...)
            OpenAiChatModel chatModel = OpenAiChatModel.builder()
                    .options(options)
                    .build();
            // 把容器里的 ToolCallingManager（平台显式限额 + 工具日志）传给默认 ToolCallingAdvisor：
            // ChatClient.builder 的 5 参重载接受自定义 ToolCallingAdvisor.Builder，工具循环即用我们的限额。
            // ⚠️ 该重载会断言 observationRegistry 非空（传 null 直接 "observationRegistry cannot be null"，实测），
            // 不用观测时按框架惯例传 NOOP。
            ChatClient.Builder clientBuilder = ChatClient.builder(chatModel);
            ToolCallingManager manager = managerProvider.getIfAvailable();
            if (manager != null) {
                clientBuilder = ChatClient.builder(chatModel, ObservationRegistry.NOOP, null, null,
                        ToolCallingAdvisor.builder().toolCallingManager(manager));
            }
            return clientBuilder
                    .defaultSystem(SystemPrompts.SYSTEM_PROMPT)
                    .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                    .defaultTools((Object[]) toolRegistry.toolArray())
                    .build();
        }

        /**
         * 当前配置对应的 ChatClient（按配置指纹缓存；配置未变时复用同一实例）。
         *
         * @param config 当前配置
         */
        public ChatClient of(AiModelConfig config) {
            String fingerprint = config.getProvider() + "|" + config.getBaseUrl() + "|"
                    + config.getModel() + "|" + config.getApiKey();
            ChatClient client = cachedClient;
            if (client == null || !fingerprint.equals(cachedFingerprint)) {
                synchronized (this) {
                    if (cachedClient == null || !fingerprint.equals(cachedFingerprint)) {
                        cachedClient = build(config);
                        cachedFingerprint = fingerprint;
                    }
                    client = cachedClient;
                }
            }
            return client;
        }

        /** 让缓存失效（配置保存后调用，下一次取用即重建） */
        public void invalidate() {
            cachedClient = null;
            cachedFingerprint = null;
        }
    }
}
