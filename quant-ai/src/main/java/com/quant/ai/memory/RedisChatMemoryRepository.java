package com.quant.ai.memory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.quant.ai.config.AiProperties;
import com.quant.ai.entity.AiChatMessage;
import com.quant.ai.mapper.AiChatMessageMapper;
import com.quant.common.util.JsonUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;

import tools.jackson.core.type.TypeReference;

/**
 * 会话记忆仓库（M5-04）：Redis 存最近窗口（供模型上下文，窗口由 MessageWindowChatMemory 控制），
 * MySQL 存全量历史（供历史回看与 Redis 失效后的上下文重建）。
 *
 * 分工说明：本仓库的 saveAll 只负责"窗口"（写 Redis）；MySQL 全量由 AiChatService 在每轮对话结束时追加，
 * 因为窗口会截断（第 21 条进来时最早一条被丢弃），在仓库层无法可靠判断"哪些是新消息"，
 * 而对话服务天然知道一轮问答的边界，写全量更准确。
 * findByConversationId 在 Redis 未命中时回落到 MySQL 最近窗口，保证重启/过期后上下文不丢。
 */
@Component
public class RedisChatMemoryRepository implements ChatMemoryRepository {

    /** Redis 记忆键前缀（后接会话 ID） */
    private static final String KEY_PREFIX = "ai:memory:";

    /** 会话列表接口单次返回的最大会话数（个人使用足够，避免全表扫描） */
    private static final int MAX_CONVERSATION_IDS = 100;

    private final StringRedisTemplate redisTemplate;

    private final AiChatMessageMapper messageMapper;

    private final AiProperties properties;

    public RedisChatMemoryRepository(StringRedisTemplate redisTemplate, AiChatMessageMapper messageMapper,
                                     AiProperties properties) {
        this.redisTemplate = redisTemplate;
        this.messageMapper = messageMapper;
        this.properties = properties;
    }

    @Override
    public List<String> findConversationIds() {
        return messageMapper.selectList(new LambdaQueryWrapper<AiChatMessage>()
                        .select(AiChatMessage::getSessionId)
                        .groupBy(AiChatMessage::getSessionId)
                        .orderByDesc(AiChatMessage::getSessionId)
                        .last("limit " + MAX_CONVERSATION_IDS))
                .stream().map(AiChatMessage::getSessionId).toList();
    }

    /**
     * 读取会话上下文：Redis 命中直接返回；未命中（过期/重启）时用 MySQL 最近窗口重建并回填 Redis。
     */
    @Override
    public List<Message> findByConversationId(String conversationId) {
        String cached = redisTemplate.opsForValue().get(KEY_PREFIX + conversationId);
        if (cached != null) {
            return deserialize(cached);
        }
        List<AiChatMessage> rows = messageMapper.selectList(new LambdaQueryWrapper<AiChatMessage>()
                .eq(AiChatMessage::getSessionId, conversationId)
                .orderByDesc(AiChatMessage::getId)
                .last("limit " + properties.getMemoryWindow()));
        if (rows.isEmpty()) {
            return List.of();
        }
        Collections.reverse(rows);
        List<Message> messages = new ArrayList<>(rows.size());
        for (AiChatMessage row : rows) {
            messages.add(toMessage(new StoredMessage(row.getRole(), row.getContent())));
        }
        saveAll(conversationId, messages);
        return messages;
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        redisTemplate.opsForValue().set(KEY_PREFIX + conversationId,
                JsonUtils.toJson(toStored(messages)),
                Duration.ofHours(properties.getMemoryTtlHours()));
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        redisTemplate.delete(KEY_PREFIX + conversationId);
    }

    /** Message → 可序列化结构（仅保留角色与文本，忽略工具调用等运行时字段） */
    private List<StoredMessage> toStored(List<Message> messages) {
        List<StoredMessage> stored = new ArrayList<>(messages.size());
        for (Message message : messages) {
            stored.add(new StoredMessage(message.getMessageType().getValue(), message.getText()));
        }
        return stored;
    }

    /** 反序列化 Redis 中的上下文（JSON 异常时返回空窗口，避免影响对话主流程） */
    private List<Message> deserialize(String json) {
        try {
            List<StoredMessage> stored = JsonUtils.mapper().readValue(json,
                    new TypeReference<List<StoredMessage>>() {
                    });
            List<Message> messages = new ArrayList<>(stored.size());
            for (StoredMessage item : stored) {
                messages.add(toMessage(item));
            }
            return messages;
        } catch (Exception e) {
            return List.of();
        }
    }

    /** 存储结构 → Spring AI 消息（本平台只有 user/assistant 两类） */
    private Message toMessage(StoredMessage stored) {
        String content = stored.content() == null ? "" : stored.content();
        return MessageType.ASSISTANT.getValue().equals(stored.role())
                ? new AssistantMessage(content) : new UserMessage(content);
    }

    /**
     * 记忆持久化结构（Redis JSON 元素）
     */
    private record StoredMessage(
            /** 角色：user/assistant */
            String role,
            /** 消息正文 */
            String content) {
    }
}
