package com.quant.strategy.core;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.quant.fund.entity.FundBasic;

import tools.jackson.databind.JsonNode;

/**
 * 策略上下文：实时信号生成用（FR5 每日信号计算）
 */
public record StrategyContext(FundBasic fund, JsonNode params, MarketDataSeries recentSeries) {
}
