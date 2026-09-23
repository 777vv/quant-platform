package com.quant.strategy.core;

import java.math.BigDecimal;
import java.time.LocalDate;

import tools.jackson.databind.JsonNode;

/**
 * 策略 SPI（技术文档 6.4）：新增策略 = 新增一个 @Component 实现类，不改框架代码
 */
public interface Strategy {

    /** 策略类型标识，对应 strategy_config.strategy_type */
    String type();

    /** 展示名 */
    String name();

    /** 参数校验（保存策略配置时调用），不合法抛 BizException */
    void validateParams(tools.jackson.databind.JsonNode params);

    /** 实时信号：基于截至最新交易日的数据计算当日建议 */
    Signal generateSignal(StrategyContext context);

    /**
     * 回测决策：基于 [0..index] 日数据与当前回测状态给出交易意向；
     * 引擎负责在 index+1 日按开盘价（ETF）/净值（场外）撮合，规避未来函数
     */
    BacktestAction decide(int index, MarketDataSeries data, BacktestState state);

    /**
     * 回测发起前的策略级前置校验（默认不校验）。
     *
     * <p>典型用途：仓位以"份数"为口径的策略（如震荡向上）需要确认初始本金买得起满仓份额，
     * 否则回测跑出来的仓位档位与配置含义不符。抛 BizException 会让该次回测置为失败并展示原因。
     *
     * @param series         回测行情序列（含预热段）
     * @param startIndex     决策起始下标（区间首日）
     * @param params         策略参数
     * @param initialCapital 初始本金
     */
    default void validateBacktest(MarketDataSeries series, int startIndex, JsonNode params,
            BigDecimal initialCapital) {
    }

    /** 安全取 BigDecimal（Jackson3 对缺失节点 decimalValue() 会抛异常，可选参数必须走此方法） */
    static BigDecimal dec(tools.jackson.databind.JsonNode node, String field, BigDecimal defaultValue) {
        tools.jackson.databind.JsonNode value = node.get(field);
        return value == null || value.isNull() ? defaultValue : value.decimalValue();
    }

    /** 安全取 int */
    static int intOr(tools.jackson.databind.JsonNode node, String field, int defaultValue) {
        tools.jackson.databind.JsonNode value = node.get(field);
        return value == null || value.isNull() ? defaultValue : value.asInt();
    }

    /** 安全取 double */
    static double dblOr(tools.jackson.databind.JsonNode node, String field, double defaultValue) {
        tools.jackson.databind.JsonNode value = node.get(field);
        return value == null || value.isNull() ? defaultValue : value.asDouble();
    }

    /** 安全取 String */
    static String strOr(tools.jackson.databind.JsonNode node, String field, String defaultValue) {
        tools.jackson.databind.JsonNode value = node.get(field);
        return value == null || value.isNull() ? defaultValue : value.asText();
    }
}
