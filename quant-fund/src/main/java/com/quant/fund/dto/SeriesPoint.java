package com.quant.fund.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * K线/净值/估值序列点（前端图表通用，按用途取相应字段）
 */
public record SeriesPoint(
        /** 日期 */
        LocalDate date,
        /** 开盘价（仅 ETF K线） */
        BigDecimal open,
        /** 收盘价（仅 ETF K线，前复权） */
        BigDecimal close,
        /** 最高价（仅 ETF K线） */
        BigDecimal high,
        /** 最低价（仅 ETF K线） */
        BigDecimal low,
        /** 成交量（仅 ETF K线，手） */
        Long volume,
        /** 单位净值（仅场外） */
        BigDecimal unitNav,
        /** 累计净值（仅场外） */
        BigDecimal accNav,
        /** 复权净值（仅场外） */
        BigDecimal adjNav,
        /** 跟踪指数 PE（仅估值序列） */
        BigDecimal pe) {

    /** 构造 ETF K线点 */
    public static SeriesPoint ofKline(LocalDate date, BigDecimal open, BigDecimal close, BigDecimal high,
                                      BigDecimal low, Long volume) {
        return new SeriesPoint(date, open, close, high, low, volume, null, null, null, null);
    }

    /** 构造场外净值点 */
    public static SeriesPoint ofNav(LocalDate date, BigDecimal unit, BigDecimal acc, BigDecimal adj) {
        return new SeriesPoint(date, null, null, null, null, null, unit, acc, adj, null);
    }

    /** 构造估值（PE）点 */
    public static SeriesPoint ofPe(LocalDate date, BigDecimal pe) {
        return new SeriesPoint(date, null, null, null, null, null, null, null, null, pe);
    }
}
