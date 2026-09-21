package com.quant.ai.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * AI 会话消息（ai_chat_message，全量历史；Redis 只保留最近窗口供模型使用）
 */
@Data
@TableName("ai_chat_message")
public class AiChatMessage {

    /** 主键，自增（同时作为历史回看的排序依据） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话 ID（关联 ai_chat_session.session_id） */
    private String sessionId;

    /** 角色：user=提问 / assistant=回答（本平台仅两种） */
    private String role;

    /** 消息正文（回答为流式拼接后的完整文本） */
    private String content;

    /** 创建时间（库默认值，按此升序回放历史） */
    private LocalDateTime createdAt;
}
