package com.quant.ai.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * AI 今日用量概览（浮窗用量条与平台配置用量卡）。
 *
 * @param date             统计日期（自然日）
 * @param requestCount     今日咨询次数（含自检与护栏拒答）
 * @param chatCount        今日对话次数（仅 CHAT，即真正计费的次数）
 * @param totalTokens      今日总 token（输入 + 输出）
 * @param promptTokens     今日输入 token
 * @param cachedTokens     今日输入中命中缓存的 token
 * @param completionTokens 今日输出 token
 * @param cost             今日估算费用（元）
 * @param estimated        今日是否存在按字符估算的流水（界面需标注「估算」）
 * @param dailyTokenLimit  每日 token 上限（0 或 null = 不限制）
 * @param dailyCostLimit   每日费用上限（元；0 或 null = 不限制）
 * @param warnPercent      预警百分比（0 = 不预警）
 * @param usedPercent      已用比例（token 与费用两个口径取大者；无上限或未填单价时为 0）
 * @param warn             是否已达预警线
 * @param overLimit        是否已超限（超限后对话被拒绝）
 * @param priceMissing     是否未填单价（费用恒为 0，界面需提示）
 * @param hint             附加提示文案（未填单价等；无需提示时为 null）
 */
public record AiUsageTodayVO(
        /** 统计日期 */
        LocalDate date,
        /** 今日咨询次数（含自检与护栏拒答） */
        int requestCount,
        /** 今日对话次数（仅真正计费的 CHAT） */
        int chatCount,
        /** 今日总 token */
        long totalTokens,
        /** 今日输入 token */
        long promptTokens,
        /** 今日缓存命中 token */
        long cachedTokens,
        /** 今日输出 token */
        long completionTokens,
        /** 今日估算费用（元） */
        BigDecimal cost,
        /** 今日是否含估算流水 */
        boolean estimated,
        /** 每日 token 上限（0/null 表示不限制） */
        Integer dailyTokenLimit,
        /** 每日费用上限（元；0/null 表示不限制） */
        BigDecimal dailyCostLimit,
        /** 预警百分比（0 表示不预警） */
        int warnPercent,
        /** 已用比例（两个口径取大者） */
        int usedPercent,
        /** 是否已达预警线 */
        boolean warn,
        /** 是否已超限 */
        boolean overLimit,
        /** 是否未填单价 */
        boolean priceMissing,
        /** 附加提示文案 */
        String hint) {
}
