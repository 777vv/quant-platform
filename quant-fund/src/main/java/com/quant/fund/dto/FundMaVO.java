package com.quant.fund.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Data;

/**
 * 市场信号【均价】页签行（V5.68）：每只基金最新一条均线快照 + 现价/均线比值。
 * 比值 = closePrice ÷ 对应周期均价，保留 3 位小数（>1 价格在均线上方，<1 下方）。
 */
@Data
public class FundMaVO {

    /** 基金代码 */
    private String fundCode;

    /** 基金名称 */
    private String fundName;

    /** 行情数据截至日（该基金最新数据日） */
    private LocalDate dataDate;

    /** 现价（最新收盘价/复权净值） */
    private BigDecimal closePrice;

    /** 5 个交易日均价 */
    private BigDecimal ma5;

    /** 10 个交易日均价 */
    private BigDecimal ma10;

    /** 20 个交易日均价 */
    private BigDecimal ma20;

    /** 30 个交易日均价 */
    private BigDecimal ma30;

    /** 60 个交易日均价 */
    private BigDecimal ma60;

    /** 90 个交易日均价 */
    private BigDecimal ma90;

    /** 120 个交易日均价 */
    private BigDecimal ma120;

    /** 250 个交易日均价 */
    private BigDecimal ma250;

    /** 现价 ÷ 5 日均价（3 位小数） */
    private BigDecimal ratio5;

    /** 现价 ÷ 10 日均价（3 位小数） */
    private BigDecimal ratio10;

    /** 现价 ÷ 20 日均价（3 位小数） */
    private BigDecimal ratio20;

    /** 现价 ÷ 30 日均价（3 位小数） */
    private BigDecimal ratio30;

    /** 现价 ÷ 60 日均价（3 位小数） */
    private BigDecimal ratio60;

    /** 现价 ÷ 90 日均价（3 位小数） */
    private BigDecimal ratio90;

    /** 现价 ÷ 120 日均价（3 位小数） */
    private BigDecimal ratio120;

    /** 现价 ÷ 250 日均价（3 位小数） */
    private BigDecimal ratio250;
}
