package com.quant.strategy.core;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 回测运行时状态（引擎维护，策略只读 + scratch 自用）
 */
public class BacktestState {

    private BigDecimal cash;

    private BigDecimal shares = BigDecimal.ZERO;

    /** 摊薄成本（含费用） */
    private BigDecimal avgCost = BigDecimal.ZERO;

    /** 最近一次买入成交日索引（T+1 卖出限制） */
    private int lastBuyIndex = -1;

    private BigDecimal realizedPnl = BigDecimal.ZERO;

    private int sellCount;

    private int winCount;

    /** 策略私有暂存（如网格锚点价） */
    private final Map<String, Object> scratch = new HashMap<>();

    public BacktestState(BigDecimal cash) {
        this.cash = cash;
    }

    public BigDecimal getCash() {
        return cash;
    }

    public void setCash(BigDecimal cash) {
        this.cash = cash;
    }

    public BigDecimal getShares() {
        return shares;
    }

    public void setShares(BigDecimal shares) {
        this.shares = shares;
    }

    public BigDecimal getAvgCost() {
        return avgCost;
    }

    public void setAvgCost(BigDecimal avgCost) {
        this.avgCost = avgCost;
    }

    public int getLastBuyIndex() {
        return lastBuyIndex;
    }

    public void setLastBuyIndex(int lastBuyIndex) {
        this.lastBuyIndex = lastBuyIndex;
    }

    public BigDecimal getRealizedPnl() {
        return realizedPnl;
    }

    public void setRealizedPnl(BigDecimal realizedPnl) {
        this.realizedPnl = realizedPnl;
    }

    public int getSellCount() {
        return sellCount;
    }

    public void setSellCount(int sellCount) {
        this.sellCount = sellCount;
    }

    public int getWinCount() {
        return winCount;
    }

    public void setWinCount(int winCount) {
        this.winCount = winCount;
    }

    public Map<String, Object> getScratch() {
        return scratch;
    }
}
