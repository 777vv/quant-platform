package com.quant.strategy.core;

import java.math.BigDecimal;
import java.time.LocalDate;

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
