package com.quant.ai.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * AI 会话（ai_chat_session，FR6 会话侧栏）
 */
@Data
@TableName("ai_chat_session")
public class AiChatSession {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话 UUID（前端持有，同时作为 ChatMemory 的 conversationId） */
    private String sessionId;

    /** 会话标题：取首条提问前若干字符，便于侧栏识别 */
    private String title;

    /** 创建时间（库默认值） */
    private LocalDateTime createdAt;

    /** 更新时间（库自动维护；有新消息时刷新，用于侧栏按最近活跃排序） */
    private LocalDateTime updatedAt;
}
