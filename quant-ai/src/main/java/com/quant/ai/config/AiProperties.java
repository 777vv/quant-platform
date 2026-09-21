package com.quant.ai.config;

import org.springframework.ai.model.tool.ToolCallLimitBehavior;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 助手配置（quant.ai.*，M5）。
 * 模型与密钥在【平台配置 → AI 模型配置】维护（存库，V3.0 起），
 * 本类承载平台侧的开关、记忆窗口、工具调用限额等业务参数。
 */
@ConfigurationProperties(prefix = "quant.ai")
public class AiProperties {

    /** AI 助手总开关：false 时对话接口直接返回"未开启"，不调用大模型 */
    private boolean enabled = true;

    /** 会话记忆窗口：送入模型的最大消息条数（超出截断最早消息；MySQL 仍保留全量） */
    private int memoryWindow = 20;

    /** 记忆窗口在 Redis 的缓存时长（小时）：到期仅影响上下文，历史由 MySQL 兜底 */
    private int memoryTtlHours = 168;

    /** 单次提问的最大字符数（超长直接拒绝，避免异常消耗 token） */
    private int maxQuestionLength = 2000;

    /** 单次对话的最长等待秒数（流式超时保护，防止上游挂死导致连接一直占用） */
    private int timeoutSeconds = 60;

    /**
     * 采样温度（平台级参数，模型切换不影响）：本助手以事实查询为主（逐字引用库内数字），
     * 温度过高易发散编造，故默认 0.3【V1.8 实测调整】。
     */
    private double temperature = 0.3;

    /**
     * 工具调用限额（V4.5）：不使用 Spring AI 默认值（单工具 40 次 / 合计 150 次过于宽松），
     * 由平台显式配置，作为防"模型反复要求调工具"的轮数闸。
     * 与每日 token/费用额度（管总量）、流式超时（管卡死）互为补充。
     */
    private ToolLimit toolLimit = new ToolLimit();

    /** 工具调用限额参数（quant.ai.tool-limit.*） */
    public static class ToolLimit {

        /**
         * 单个工具在一次对话里最多被调用的次数；&lt;=0 表示不限制。
         * 默认 6：实测最"贪"的场景（持仓+走势+信号复合问题）通常 2~3 轮，留一倍余量。
         */
        private int maxCallsPerTool = 6;

        /**
         * 一次对话里全部工具合计的调用上限；&lt;=0 表示不限制。
         * 默认 12：把最坏情况的 token 封顶（每轮都重发系统提示词与工具 schema）。
         */
        private int maxTotalToolCalls = 12;

        /**
         * 超限后的行为：RETURN_ERROR_RESPONSE（默认，把"不可再调"作为错误结果回给模型，
         * 让它改用文字收尾，用户侧只觉得回答变短）/ THROW（直接抛异常断流，已生成的半截回答也会断）。
         */
        private String onLimitExceeded = "RETURN_ERROR_RESPONSE";

        /** 解析行为枚举（配置写错时给中文提示，而不是启动期抛看不懂的 IllegalArgumentException） */
        public ToolCallLimitBehavior behavior() {
            try {
                return ToolCallLimitBehavior.valueOf(onLimitExceeded.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException(
                        "quant.ai.tool-limit.on-limit-exceeded 取值非法：" + onLimitExceeded
                                + "（可选 RETURN_ERROR_RESPONSE / THROW）", e);
            }
        }

        public int getMaxCallsPerTool() {
            return maxCallsPerTool;
        }

        public void setMaxCallsPerTool(int maxCallsPerTool) {
            this.maxCallsPerTool = maxCallsPerTool;
        }

        public int getMaxTotalToolCalls() {
            return maxTotalToolCalls;
        }

        public void setMaxTotalToolCalls(int maxTotalToolCalls) {
            this.maxTotalToolCalls = maxTotalToolCalls;
        }

        public String getOnLimitExceeded() {
            return onLimitExceeded;
        }

        public void setOnLimitExceeded(String onLimitExceeded) {
            this.onLimitExceeded = onLimitExceeded;
        }
    }

    public ToolLimit getToolLimit() {
        return toolLimit;
    }

    public void setToolLimit(ToolLimit toolLimit) {
        this.toolLimit = toolLimit;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMemoryWindow() {
        return memoryWindow;
    }

    public void setMemoryWindow(int memoryWindow) {
        this.memoryWindow = memoryWindow;
    }

    public int getMemoryTtlHours() {
        return memoryTtlHours;
    }

    public void setMemoryTtlHours(int memoryTtlHours) {
        this.memoryTtlHours = memoryTtlHours;
    }

    public int getMaxQuestionLength() {
        return maxQuestionLength;
    }

    public void setMaxQuestionLength(int maxQuestionLength) {
        this.maxQuestionLength = maxQuestionLength;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
}
