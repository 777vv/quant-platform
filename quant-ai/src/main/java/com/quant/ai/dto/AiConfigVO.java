package com.quant.ai.dto;

import java.util.Map;

/**
 * 会话配置视图（FR6 浮窗页脚展示：模型与配置状态）
 */
public record AiConfigVO(
        /** AI 助手是否启用（quant.ai.enabled） */
        boolean enabled,
        /** 是否已配置 API Key（未配置时对话不可用，前端给出引导） */
        boolean configured,
        /** 当前模型名（spring.ai.openai.chat.options.model） */
        String model,
        /** 会话窗口大小（送入模型的最大消息条数） */
        int memoryWindow,
        /** 界面展示用的额外信息（如未配置原因） */
        Map<String, String> extras) {
}
