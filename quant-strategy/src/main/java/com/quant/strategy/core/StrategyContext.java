package com.quant.strategy.core;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.quant.fund.entity.FundBasic;

import tools.jackson.databind.JsonNode;

/**
 * 策略上下文：实时信号生成用（FR5 每日信号计算）。
 *
 * <p>除行情与参数外，还带上"当前仓位"与"最近一次实际交易"，供需要持仓状态的策略使用
 * （如<b>震荡向上</b>：档位判断要当前份额、首次/二次判断要最近实际交易的方向/价位/日期）；
 * 不关心这些字段的策略（网格、估值百分位）忽略即可。
 *
 * <p>⚠️ "实际交易" = 交易流水（trade_flow）里的买入/卖出，**不是信号**——
 * 用户口径（V5.9）：信号发了但没照做（无对应流水）时信号不算数，状态机以实际成交为准。
 *
 * @param fund               基金档案
 * @param params             策略参数
 * @param recentSeries       回看窗口内的行情序列
 * @param currentShares      当前持仓份额（取平台记录的实际持仓；无持仓为 0）
 * @param lastTradeDirection 最近一次实际交易方向（BUY/SELL；null = 还没有过买卖流水，即"起步"）
 * @param lastTradePrice     最近一次实际交易的成交价（trade_flow.price；null = 起步）
 * @param lastTradeDate      最近一次实际交易的日期（trade_flow.trade_date；null = 起步）
 */
public record StrategyContext(FundBasic fund, JsonNode params, MarketDataSeries recentSeries,
        BigDecimal currentShares, String lastTradeDirection, BigDecimal lastTradePrice,
        LocalDate lastTradeDate) {
}
