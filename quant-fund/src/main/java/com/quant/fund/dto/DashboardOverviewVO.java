package com.quant.fund.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 仪表盘速览区（FR1，M4-08）：持仓概览 / 自选涨跌榜 / 配置占比 / 同步状态
 */
public record DashboardOverviewVO(
        /** 持仓概览（按市值降序，最多 5 条） */
        List<HoldingBrief> holdings,
        /** 自选基金近 7 日涨跌幅（降序，前端取头部为领涨、尾部为领跌） */
        List<MoverItem> movers,
        /** 持仓配置占比（饼图数据） */
        List<AllocationItem> allocation,
        /** 各基金数据同步新鲜度 */
        List<SyncStatusItem> syncStatus,
        /** 同步状态汇总文案，如 "4/4 正常" */
        String syncSummary) {

    /**
     * 持仓概览条目
     */
    public record HoldingBrief(
            /** 基金代码 */
            String fundCode,
            /** 基金简称 */
            String fundName,
            /** 持仓市值（元） */
            BigDecimal marketValue,
            /** 占总资产比（%） */
            BigDecimal weightPct,
            /** 当日盈亏（元） */
            BigDecimal dayPnl,
            /** 浮动盈亏（元） */
            BigDecimal floatingPnl) {
    }

    /**
     * 自选 7 日涨跌条目
     */
    public record MoverItem(
            /** 基金代码 */
            String fundCode,
            /** 基金简称 */
            String fundName,
            /** 近 7 日涨跌幅（%） */
            BigDecimal changePct5d) {
    }

    /**
     * 配置占比条目
     */
    public record AllocationItem(
            /** 基金代码 */
            String fundCode,
            /** 基金简称 */
            String fundName,
            /** 持仓市值（元） */
            BigDecimal marketValue,
            /** 占总资产比（%） */
            BigDecimal pct) {
    }

    /**
     * 同步状态条目
     */
    public record SyncStatusItem(
            /** 基金代码 */
            String fundCode,
            /** 基金简称 */
            String fundName,
            /** 1=场内ETF 2=场外指数基金 */
            Integer fundType,
            /** 本地最新数据日期（yyyy-MM-dd） */
            String lastDataDate,
            /** 应达最新数据日期（按周末近似推算，节假日会偏严） */
            String expectedDate,
            /** NORMAL=正常 LAGGING=滞后 */
            String status) {
    }
}
