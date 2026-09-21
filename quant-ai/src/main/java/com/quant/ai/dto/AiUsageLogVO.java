package com.quant.ai.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 用量明细行（平台配置 → 用量明细表）。
 *
 * @param id               流水 ID
 * @param sessionId        会话 ID（无会话为 null）
 * @param sessionTitle     会话标题（取自 ai_chat_session，便于识别"问的是哪次对话"）
 * @param biz              用途代码（CHAT/HEALTH/GUARD）
 * @param bizName          用途中文名
 * @param provider         厂商代码（快照）
 * @param providerName     厂商展示名
 * @param model            模型名（快照）
 * @param rounds           上游请求次数（工具调用会多轮）
 * @param promptTokens     输入 token
 * @param cachedTokens     缓存命中输入 token
 * @param completionTokens 输出 token
 * @param totalTokens      总 token
 * @param estimated        是否按字符估算
 * @param cost             估算费用（元）
 * @param questionChars    提问字数
 * @param answerChars      回答字数
 * @param durationMs       耗时（毫秒）
 * @param success          是否成功
 * @param errorMsg         失败原因
 * @param createdAt        发生时间
 */
public record AiUsageLogVO(
        /** 流水 ID */
        Long id,
        /** 会话 ID */
        String sessionId,
        /** 会话标题 */
        String sessionTitle,
        /** 用途代码 */
        String biz,
        /** 用途中文名 */
        String bizName,
        /** 厂商代码 */
        String provider,
        /** 厂商展示名 */
        String providerName,
        /** 模型名 */
        String model,
        /** 上游请求次数 */
        int rounds,
        /** 输入 token */
        int promptTokens,
        /** 缓存命中输入 token */
        int cachedTokens,
        /** 输出 token */
        int completionTokens,
        /** 总 token */
        int totalTokens,
        /** 是否按字符估算 */
        boolean estimated,
        /** 估算费用（元） */
        BigDecimal cost,
        /** 提问字数 */
        int questionChars,
        /** 回答字数 */
        int answerChars,
        /** 耗时（毫秒） */
        Integer durationMs,
        /** 是否成功 */
        boolean success,
        /** 失败原因 */
        String errorMsg,
        /** 发生时间 */
        LocalDateTime createdAt) {
}
