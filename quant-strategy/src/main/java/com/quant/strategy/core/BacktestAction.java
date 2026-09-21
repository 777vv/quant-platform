package com.quant.strategy.core;

import java.math.BigDecimal;

/**
 * 回测交易意向（引擎次日撮合）
 */
public record BacktestAction(String type, BigDecimal share, String reason) {

    public static final String BUY = "BUY";

    public static final String SELL = "SELL";

    public static final String HOLD = "HOLD";

    public static BacktestAction hold() {
        return new BacktestAction(HOLD, BigDecimal.ZERO, "");
    }

    public static BacktestAction buy(BigDecimal share, String reason) {
        return new BacktestAction(BUY, share, reason);
    }

    public static BacktestAction sell(BigDecimal share, String reason) {
        return new BacktestAction(SELL, share, reason);
    }
}
