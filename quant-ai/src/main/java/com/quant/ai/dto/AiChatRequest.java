package com.quant.ai.dto;

import lombok.Data;

/**
 * AI 对话请求（FR6）
 */
@Data
public class AiChatRequest {

    /** 会话 ID：为空表示新建会话；前端再次提问时回传，保证上下文连续 */
    private String sessionId;

    /** 用户提问内容 */
    private String question;

    /** 是否流式返回：为空按 true 处理（仅 /api/ai/chat/sync 使用 false 语义） */
    private Boolean stream;
}
