package com.quant.ai.dto;

import java.time.LocalDateTime;

/**
 * AI 会话列表条目（FR6 会话侧栏）
 */
public record AiSessionVO(
        /** 会话 ID（前端发起对话时回传） */
        String sessionId,
        /** 会话标题（首条提问摘要） */
        String title,
        /** 最近活跃时间（侧栏排序依据） */
        LocalDateTime updatedAt) {
}
