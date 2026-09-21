package com.quant.ai.dto;

import java.math.BigDecimal;

/**
 * AI 每日额度保存请求（全局一份，V4.0）。
 *
 * @param dailyTokenLimit 每日 token 上限（0 = 不限制；留空 = 保持原值）
 * @param dailyCostLimit  每日费用上限（元；0 = 不限制；留空 = 保持原值）
 * @param warnPercent     预警百分比（0~100；0 = 不预警；留空 = 保持原值）
 */
public record AiQuotaRequest(
        /** 每日 token 上限（0=不限制，空=保持原值） */
        Integer dailyTokenLimit,
        /** 每日费用上限（元；0=不限制，空=保持原值） */
        BigDecimal dailyCostLimit,
        /** 预警百分比（0=不预警，空=保持原值） */
        Integer warnPercent) {
}
