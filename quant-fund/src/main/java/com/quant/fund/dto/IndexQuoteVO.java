package com.quant.fund.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 全球指数看板条目（FR1，M4-06）：最新行情 + 近 30 交易日收盘走势迷你线
 */
public record IndexQuoteVO(
        /** 东财 secid，如 100.DJIA */
        String indexCode,
        /** 指数名称 */
        String indexName,
        /** 区域 CN/HK/US/ASIA/EU */
        String region,
        /** 最新点位 */
        BigDecimal lastPrice,
        /** 涨跌额 */
        BigDecimal changeAmt,
        /** 涨跌幅（%） */
        BigDecimal changePct,
        /** 行情时间（东八区） */
        LocalDateTime quoteTime,
        /** 近 30 交易日收盘点位（sparkline 数据源，按日期升序） */
        List<TrendPoint> trend) {

    /**
     * 走势采样点
     */
    public record TrendPoint(
            /** 采样日期（yyyy-MM-dd） */
            String time,
            /** 当日收盘点位 */
            BigDecimal price) {
    }
}
