package com.quant.fund.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 基金最新行情快照
 */
public record LastQuote(
        /** 最新价：ETF=前复权收盘价，场外=单位净值 */
        BigDecimal price,
        /** 上一交易日价格（算涨跌用，首日为 null） */
        BigDecimal prevPrice,
        /** 涨跌幅%（(price/prev-1)*100，保留2位） */
        BigDecimal changePct,
        /** 行情日期 */
        LocalDate date) {
}
