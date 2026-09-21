package com.quant.strategy.core;

import java.math.BigDecimal;

/**
 * 实时买卖建议信号
 */
public record Signal(String direction, BigDecimal price, String suggestDesc) {

    public static final String BUY = "BUY";

    public static final String SELL = "SELL";

    public static final String HOLD = "HOLD";
}
