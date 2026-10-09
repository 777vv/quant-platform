package com.quant.strategy.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import lombok.Data;

/**
 * 批量回测发起请求（V5.96）：一套策略参数 × N 只基金（用户拍板：整批统一一套参数，不做按基金配置模式）。
 */
@Data
public class BatchBacktestRequest {

    /** 策略类型码（整批统一，须在策略注册表中） */
    private String strategyType;

    /** 策略参数（整批统一；键名与单次回测一致，如 fullShare/baselineMaDays…） */
    private java.util.Map<String, Object> params;

    /** 回测开始日期（含，整批统一） */
    private LocalDate startDate;

    /** 回测结束日期（含，整批统一） */
    private LocalDate endDate;

    /**
     * 统一初始资金（元）。为空 = 按基金自动计算（满仓份额 × 开始日期价 × 1.01，与基金详情页 V5.76 同口径）；
     * 填了 = 整批所有基金用这一个数。
     */
    private BigDecimal initialCapital;

    /** 要回测的基金代码列表（须在自选池内，自动去重） */
    private List<String> fundCodes;
}
