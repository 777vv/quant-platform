package com.quant.ai.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 模型配置视图（回传给前端；对应**某个厂商**那一行的配置，V4.0）。
 *
 * @param provider        厂商代码
 * @param providerName    厂商展示名
 * @param baseUrl         OpenAI 兼容端点
 * @param model           模型名
 * @param hasKey          该厂商是否已配置 Token
 * @param keyMasked       Token 打码展示（如 sk-****cdef；不回传明文）
 * @param configured      该厂商是否已可对话（Token、Base URL、模型齐全）
 * @param active          该厂商**是否是当前启用**的厂商（对话走它）
 * @param inputPrice      输入单价（元/百万 token；0 = 不计算费用）
 * @param cacheInputPrice 缓存命中输入单价（元/百万 token；0 = 按输入单价计）
 * @param outputPrice     输出单价（元/百万 token；0 = 不计算费用）
 * @param updatedAt       该厂商配置的最近修改时间
 * @param hint            提示文案（未配置时给出下一步指引）
 */
public record AiModelConfigVO(
        /** 厂商代码 */
        String provider,
        /** 厂商展示名 */
        String providerName,
        /** OpenAI 兼容端点 */
        String baseUrl,
        /** 模型名 */
        String model,
        /** 该厂商是否已配置 Token */
        boolean hasKey,
        /** Token 打码展示 */
        String keyMasked,
        /** 该厂商是否已可对话 */
        boolean configured,
        /** 是否当前启用的厂商 */
        boolean active,
        /** 输入单价（元/百万 token） */
        BigDecimal inputPrice,
        /** 缓存命中输入单价（元/百万 token） */
        BigDecimal cacheInputPrice,
        /** 输出单价（元/百万 token） */
        BigDecimal outputPrice,
        /** 最近修改时间 */
        LocalDateTime updatedAt,
        /** 提示文案 */
        String hint) {
}
