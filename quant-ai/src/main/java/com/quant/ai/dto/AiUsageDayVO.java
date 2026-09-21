package com.quant.ai.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * AI 用量按日汇总（近 N 日趋势图）。
 *
 * @param date         日期（自然日）
 * @param requestCount 当日咨询次数
 * @param totalTokens  当日总 token
 * @param cost         当日估算费用（元）
 */
public record AiUsageDayVO(
        /** 日期 */
        LocalDate date,
        /** 当日咨询次数 */
        int requestCount,
        /** 当日总 token */
        long totalTokens,
        /** 当日估算费用（元） */
        BigDecimal cost) {
}
