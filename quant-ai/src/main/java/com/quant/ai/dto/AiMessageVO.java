package com.quant.ai.dto;

import java.time.LocalDateTime;

/**
 * AI 会话历史消息（FR6 历史回看）
 */
public record AiMessageVO(
        /** 角色：user=用户提问 / assistant=助手回答 */
        String role,
        /** 消息正文（Markdown 文本） */
        String content,
        /** 消息时间 */
        LocalDateTime createdAt) {
}
