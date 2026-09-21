package com.quant.ai.config;

import java.util.List;

import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 工具调用日志装饰器（V4.5）：包住容器里的 {@link ToolCallingManager}，把每次
 * "模型要求调哪些工具 + 入参 JSON + 执行结果摘要"记进日志文件。
 *
 * <p>为什么用它而不是开框架的观测开关：Spring AI 的
 * {@code spring.ai.tools.observations.include-content=true} 依赖 Micrometer Observation
 * 输出到特定监控系统，本地日志文件里不好检索；直接在执行入口打一行结构化日志，
 * grep `AI-TOOL` 就能看到一轮对话的完整工具链。
 *
 * <p>日志格式（一行一次批量调用，一次对话多轮就有多行）：
 * {@code AI-TOOL 调用工具: 1) getFundQuote 入参={"fundCode":"515080"} 结果(118字)=最新价1.539...}
 *
 * <p>结果摘要刻意截断（默认 300 字）：日志只用于排查"调了什么、传了什么、返回大概是什么"，
 * 全文在数据库与前端回答里都有，避免把日志撑爆。
 */
public class LoggingToolCallingManager implements ToolCallingManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("com.quant.ai.tool");

    /** 单个工具结果记进日志的最大字符数 */
    private static final int RESULT_PREVIEW_CHARS = 300;

    /** 单个工具入参记进日志的最大字符数（防异常超长入参刷屏） */
    private static final int ARGS_PREVIEW_CHARS = 600;

    private final ToolCallingManager delegate;

    public LoggingToolCallingManager(ToolCallingManager delegate) {
        this.delegate = delegate;
    }

    @Override
    public List<org.springframework.ai.tool.definition.ToolDefinition> resolveToolDefinitions(
            org.springframework.ai.model.tool.ToolCallingChatOptions options) {
        return delegate.resolveToolDefinitions(options);
    }

    @Override
    public ToolExecutionResult executeToolCalls(Prompt prompt, ChatResponse response) {
        logRequestedCalls(response);
        ToolExecutionResult result = delegate.executeToolCalls(prompt, response);
        logExecutionResults(result);
        return result;
    }

    /** 执行前：记下模型这一轮要求调用的工具名与入参 */
    private void logRequestedCalls(ChatResponse response) {
        if (response == null || !response.hasToolCalls() || response.getResults() == null) {
            return;
        }
        StringBuilder line = new StringBuilder("AI-TOOL 调用工具:");
        int index = 0;
        // 单帧响应里工具调用在 generation 的 AssistantMessage 上（与 toEvents 的取法一致）
        for (var generation : response.getResults()) {
            if (generation == null || generation.getOutput() == null
                    || !(generation.getOutput() instanceof org.springframework.ai.chat.messages.AssistantMessage assistant)
                    || !assistant.hasToolCalls()) {
                continue;
            }
            for (var call : assistant.getToolCalls()) {
                line.append("\n  ").append(++index).append(") ")
                        .append(nullSafe(call.name()))
                        .append(" 入参=").append(preview(call.arguments(), ARGS_PREVIEW_CHARS));
            }
        }
        if (index > 0) {
            LOGGER.info(line.toString());
        }
    }

    /** 执行后：按同一顺序记每个工具的结果摘要（框架保证与请求顺序一致） */
    private void logExecutionResults(ToolExecutionResult result) {
        if (result == null || result.conversationHistory() == null) {
            return;
        }
        // 结果消息是追加在历史尾部的 role=tool 消息，顺序与请求的 tool_calls 一致
        StringBuilder line = new StringBuilder("AI-TOOL 工具结果:");
        int index = 0;
        for (var message : result.conversationHistory()) {
            if (message instanceof org.springframework.ai.chat.messages.ToolResponseMessage toolMessage) {
                for (var response : toolMessage.getResponses()) {
                    line.append("\n  ").append(++index).append(") ")
                            .append(nullSafe(response.name()))
                            .append(" 结果(").append(response.responseData() == null ? 0 : response.responseData().length())
                            .append("字)=").append(preview(response.responseData(), RESULT_PREVIEW_CHARS));
                }
            }
        }
        if (index > 0) {
            LOGGER.info(line.toString());
        }
    }

    /** 单行摘要：换行折叠为空格 + 截断，保证日志里一次调用就是一行 */
    private String preview(String text, int maxChars) {
        if (text == null) {
            return "null";
        }
        String flat = text.replaceAll("\\s+", " ").strip();
        return flat.length() <= maxChars ? flat : flat.substring(0, maxChars) + "…(截断)";
    }

    private String nullSafe(String value) {
        return value == null ? "null" : value;
    }
}
