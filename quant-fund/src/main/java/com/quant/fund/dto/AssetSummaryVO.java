package com.quant.fund.dto;

import java.math.BigDecimal;

/**
 * 仪表盘资产总览（FR1，M4-01）。
 * 总资产口径（V1.9 ㊾）：总资产 = 持仓市值 + 现金余额；
 * 现金余额 = Σ资金转入 − Σ资金转出 − 净投入（净投入=买入含费−卖出净额−分红净额）。
 */
public record AssetSummaryVO(
        /** 总资产（元）= 持仓市值 + 现金余额 */
        BigDecimal totalAssets,
        /** 现金余额（元）= 转入 − 转出 − 净投入；未录过划转时可能为负（提示补录划转） */
        BigDecimal cashBalance,
        /** 持仓总市值（元）= Σ 最新价/净值 × 持有份额 */
        BigDecimal marketValue,
        /** 持仓摊薄总成本（元） */
        BigDecimal totalCost,
        /** 当日盈亏（元）= Σ (现价 - 昨价) × 份额 */
        BigDecimal dayPnl,
        /** 浮动盈亏（元）= 总市值 - 摊薄总成本 */
        BigDecimal floatingPnl,
        /** 浮动盈亏率（%，总成本为 0 时为 null） */
        BigDecimal floatingPnlPct,
        /** 累计已实现盈亏（元，含卖出与分红） */
        BigDecimal realizedPnl,
        /** 累计收益（元）= 浮动盈亏 + 已实现盈亏 */
        BigDecimal totalPnl,
        /** 累计收益率（%，分母=净投入：累计买入净额-累计卖出/分红净额；净投入&lt;=0 时为 null） */
        BigDecimal totalPnlPct,
        /** 近 7 日收益（元）= 今日累计收益 - 7 个自然日前累计收益 */
        BigDecimal weekPnl,
        /** 本月收益（元）= 今日累计收益 - 上月末累计收益 */
        BigDecimal monthPnl,
        /** 本月收益率（%，分母=上月末持仓市值；无上月末数据时为 null） */
        BigDecimal monthPnlPct,
        /** 本年收益（元）= 今日累计收益 - 上年末累计收益 */
        BigDecimal yearPnl,
        /** 本年收益率（%，分母=上年末持仓市值；无上年末数据时为 null） */
        BigDecimal yearPnlPct,
        /** 持仓基金数量 */
        Integer holdingCount,
        /** 自选基金数量 */
        Integer watchCount) {
}
