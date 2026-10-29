package com.quant.fund.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 市场信号「区间涨跌」页签行视图（V6.63 用户要求）。
 *
 * <p>口径：取基金在所选日期区间内**实际有数据的首日到末日**计算（区间起点早于基金最早数据时，
 * 按最早有数据那一天起算，统计区间如实回显）；区间内不足 2 个数据点的基金不返回。
 *
 * @param fundCode        基金代码
 * @param fundName        基金简称
 * @param fundType        1=场内ETF 2=场外指数基金（决定行情弹框的 K线/净值口径）
 * @param changePct       区间涨跌幅（%）= 末日价 ÷ 首日价 − 1
 * @param startDate       实际统计起始日（区间内首个有数据的交易日）
 * @param endDate         实际统计截止日（区间内最后一个有数据的交易日）
 * @param tradeDays       交易日数（实际使用的数据点个数）
 * @param annualizedPct   年化收益率（%）：(末日/首日)^(365/实际自然日跨度) − 1；跨度不足 1 天时为 null
 * @param maxDrawdownPct  最大回撤（%，负数）：区间内峰值到谷值的最大跌幅
 */
public record RangePerformanceVO(
        String fundCode,
        String fundName,
        Integer fundType,
        BigDecimal changePct,
        LocalDate startDate,
        LocalDate endDate,
        int tradeDays,
        BigDecimal annualizedPct,
        BigDecimal maxDrawdownPct) {
}
