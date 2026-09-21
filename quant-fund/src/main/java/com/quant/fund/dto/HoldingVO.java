package com.quant.fund.dto;

import java.math.BigDecimal;

/**
 * 持仓基金列表条目
 */
public record HoldingVO(
        /** 基金代码 */
        String fundCode,
        /** 基金简称 */
        String fundName,
        /** 1=场内ETF 2=场外指数基金 */
        Integer fundType,
        /** 类型展示名 */
        String fundTypeDesc,
        /** 持有份额 */
        BigDecimal totalShare,
        /** 摊薄成本价（含费用） */
        BigDecimal avgCostPrice,
        /** 最新价/净值 */
        BigDecimal lastPrice,
        /** 持仓市值 = 最新价 × 份额 */
        BigDecimal marketValue,
        /** 当日盈亏 = (现价-昨价) × 份额 */
        BigDecimal dayPnl,
        /** 浮动盈亏 = 市值 - 摊薄成本 */
        BigDecimal floatingPnl,
        /** 浮动盈亏百分比 */
        BigDecimal floatingPnlPct,
        /** 累计已实现盈亏（卖出+分红-结转成本） */
        BigDecimal realizedPnl) {
}
