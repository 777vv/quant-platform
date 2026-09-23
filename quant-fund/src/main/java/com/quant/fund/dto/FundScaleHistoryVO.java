package com.quant.fund.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 基金规模历史点视图（行情图「基金规模」副图数据源）。
 *
 * @param date  统计日期（档案刷新成功那天）
 * @param scale 净资产规模（亿元）
 */
public record FundScaleHistoryVO(LocalDate date, BigDecimal scale) {
}
