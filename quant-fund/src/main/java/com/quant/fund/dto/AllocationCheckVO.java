package com.quant.fund.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 仓位配置检查结果（V5.36）。
 *
 * @param evaluated   是否完成评估（总资产 &le; 0 时为 false，比例无从谈起）
 * @param totalAssets 总资产（元）= 持仓市值 + 现金余额
 * @param cashBalance 现金余额（元）
 * @param rows        五类资产的检查明细
 */
public record AllocationCheckVO(
        boolean evaluated,
        BigDecimal totalAssets,
        BigDecimal cashBalance,
        List<Row> rows) {

    /**
     * 单类别检查行。
     *
     * @param key        类别键（cash / a_share / us / asia / eu）
     * @param label      类别中文名
     * @param amount     该类别当前金额（元）
     * @param currentPct 当前占比（%）
     * @param minPct     配置下限（%）
     * @param maxPct     配置上限（%）
     * @param ok         是否在范围内
     */
    public record Row(String key, String label, BigDecimal amount, BigDecimal currentPct,
                      BigDecimal minPct, BigDecimal maxPct, boolean ok) {
    }
}
