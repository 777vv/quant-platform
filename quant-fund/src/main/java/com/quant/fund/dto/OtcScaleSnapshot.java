package com.quant.fund.dto;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 场外规模快照（V6.28，内部缓存载体）：Redis 键 import:otc-scale:latest、缓存 7 天（季报数据一年只变 4 次）。
 * reportDate = 最近一个已完整披露的季报截止日（按法定披露截止日推导，如 2026-06-30）；
 * scaleYi = 基金代码 → 期末净资产（亿元，东财指数型信息列表 EndNav 原值，未四舍五入）。
 */
public record OtcScaleSnapshot(
        /** 季报截止日（yyyy-MM-dd） */
        String reportDate,
        /** 基金代码 → 期末净资产（亿元） */
        Map<String, BigDecimal> scaleYi) {
}
