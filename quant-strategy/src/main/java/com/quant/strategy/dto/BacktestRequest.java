package com.quant.strategy.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Data;

/**
 * 发起回测请求
 */
@Data
public class BacktestRequest {

    /** 基金代码（须在自选池且有区间数据） */
    private String fundCode;

    /** 策略类型 */
    private String strategyType;

    /** 策略参数（估值策略会自动前推窗口年数加载预热数据） */
    private java.util.Map<String, Object> params;

    /** 回测开始日期（含） */
    private LocalDate startDate;

    /** 回测结束日期（含，通常为最近交易日） */
    private LocalDate endDate;

    /** 初始资金（元） */
    private BigDecimal initialCapital;
}
