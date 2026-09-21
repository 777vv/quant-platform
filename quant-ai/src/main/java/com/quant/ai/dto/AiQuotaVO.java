package com.quant.ai.dto;

import java.math.BigDecimal;

/**
 * AI 每日额度视图（全局一份，V4.0；界面在【AI用量统计】页）。
 *
 * @param dailyTokenLimit 每日 token 上限（0 = 不限制）
 * @param dailyCostLimit  每日费用上限（元；0 = 不限制）
 * @param warnPercent     预警百分比（0 = 不预警）
 */
public record AiQuotaVO(
        /** 每日 token 上限（0=不限制） */
        Integer dailyTokenLimit,
        /** 每日费用上限（元；0=不限制） */
        BigDecimal dailyCostLimit,
        /** 预警百分比（0=不预警） */
        Integer warnPercent) {
}
