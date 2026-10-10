package com.quant.fund.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 基金规模历史点视图（行情图「基金规模」副图数据源）。
 *
 * @param date   统计日期（档案刷新/估算成功那天）
 * @param scale  净资产规模（亿元）
 * @param source 口径：DISCLOSED=定期报告披露值 / ESTIMATED=每日估算（份额 × 单位净值）；
 *               前端「规模副图」优先画 ESTIMATED（连续每日），没有时回退 DISCLOSED
 */
public record FundScaleHistoryVO(LocalDate date, BigDecimal scale, String source) {
}
