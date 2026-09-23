package com.quant.strategy.core;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.quant.strategy.entity.BacktestTradeDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 回测撮合引擎（技术文档 6.5）：
 * 当日收盘产生信号 → 次日开盘价（ETF）/次日净值（场外）成交；T+1；现金不足按 1% 步进折算份额；
 * ETF 佣金 max(费率×金额, 最低佣金)；场外申购/赎回按费率。
 */
@Component
public class BacktestEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(BacktestEngine.class);

    private final FeeProperties feeProperties;

    public BacktestEngine(FeeProperties feeProperties) {
        this.feeProperties = feeProperties;
    }

    /**
     * 回测结果：交易明细 + 资金/回撤/基准曲线（[date, value] 数组）+ 平仓统计
     */
    public record BacktestResult(List<BacktestTradeDetail> trades, List<Object[]> equityCurve,
                                 BigDecimal avgPositionShare, BigDecimal avgPositionValue,
                                 BigDecimal avgPositionCost, BigDecimal positionReturnPct,
                                 List<Object[]> drawdownCurve, List<Object[]> benchmarkCurve,
                                 BigDecimal finalAssets, int sellCount, int winCount) {
    }

    public BacktestResult execute(Strategy strategy, tools.jackson.databind.JsonNode params, MarketDataSeries data,
                                   BigDecimal initialCapital) {
        return execute(strategy, params, data, initialCapital, 0);
    }

    /**
     * @param startIndex 决策起始索引（之前的 warmup 段仅供策略回看窗口，不产生交易；基准亦从此日建仓）
     */
    public BacktestResult execute(Strategy strategy, tools.jackson.databind.JsonNode params, MarketDataSeries data,
                                   BigDecimal initialCapital, int startIndex) {
        BacktestState state = new BacktestState(initialCapital);
        state.getScratch().put("params", params);
        List<BacktestTradeDetail> trades = new ArrayList<>();
        List<Object[]> equityCurve = new ArrayList<>(data.size());
        List<Object[]> drawdownCurve = new ArrayList<>(data.size());
        List<Object[]> benchmarkCurve = new ArrayList<>(data.size());
        BacktestAction pending = BacktestAction.hold();
        BigDecimal peak = initialCapital;
        BigDecimal benchmarkShares = BigDecimal.ZERO;
        BigDecimal shareSum = BigDecimal.ZERO;
        BigDecimal valueSum = BigDecimal.ZERO;
        BigDecimal costSum = BigDecimal.ZERO;
        int shareDays = 0;
        for (int i = 0; i < data.size(); i++) {
            MarketDataSeries.DayPoint point = data.get(i);
            // 1) 执行前一交易日的信号：ETF 次日开盘成交，场外按次日净值
            if (i > startIndex && !BacktestAction.HOLD.equals(pending.type())) {
                BigDecimal execPrice = data.isEtf() && point.open() != null ? point.open() : point.close();
                executeAction(pending, execPrice, i, data.isEtf(), point.date(), state, trades);
                pending = BacktestAction.hold();
            }
            // 2) 收盘决策（数据末日前才可挂单次日成交）
            if (i >= startIndex) {
                try {
                    BacktestAction action = strategy.decide(i, data, state);
                    if (!BacktestAction.HOLD.equals(action.type()) && i + 1 < data.size()) {
                        pending = action;
                    }
                } catch (Exception e) {
                    LOGGER.warn("策略决策异常(index={}): {}", i, e.getMessage());
                }
            }
            // 3) 收盘估值与基准（起始日收盘全仓买入持有）
            if (i == startIndex && point.close().compareTo(BigDecimal.ZERO) > 0) {
                benchmarkShares = initialCapital.divide(point.close(), 4, RoundingMode.HALF_UP);
            }
            // 决策期逐日累计持仓份额与持仓市值（回测结束后取平均 → 「平均仓位份额 / 平均持仓市值」）
            if (i >= startIndex) {
                shareSum = shareSum.add(state.getShares());
                valueSum = valueSum.add(state.getShares().multiply(point.close()));
                // 持仓成本基础 = 摊薄成本 × 份额（卖出按份额比例退出、加仓按加权摊薄，等于"剩余持仓的实际投入"）
                costSum = costSum.add(state.getAvgCost().multiply(state.getShares()));
                shareDays++;
            }
            BigDecimal equity = state.getCash()
                    .add(state.getShares().multiply(point.close())).setScale(2, RoundingMode.HALF_UP);
            BigDecimal benchmark = benchmarkShares.multiply(point.close()).setScale(2, RoundingMode.HALF_UP);
            equityCurve.add(new Object[]{point.date(), equity});
            benchmarkCurve.add(new Object[]{point.date(), benchmark});
            if (equity.compareTo(peak) > 0) {
                peak = equity;
            }
            drawdownCurve.add(new Object[]{point.date(), peak.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO
                    : equity.divide(peak, 6, RoundingMode.HALF_UP)
                            .subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(100))});
        }
        BigDecimal finalAssets = equityCurve.isEmpty() ? initialCapital
                : (BigDecimal) equityCurve.get(equityCurve.size() - 1)[1];
        BigDecimal avgPositionShare = shareDays == 0 ? BigDecimal.ZERO
                : shareSum.divide(BigDecimal.valueOf(shareDays), 2, RoundingMode.HALF_UP);
        BigDecimal avgPositionValue = shareDays == 0 ? BigDecimal.ZERO
                : valueSum.divide(BigDecimal.valueOf(shareDays), 2, RoundingMode.HALF_UP);
        BigDecimal avgPositionCost = shareDays == 0 ? BigDecimal.ZERO
                : costSum.divide(BigDecimal.valueOf(shareDays), 2, RoundingMode.HALF_UP);
        // 持仓资产收益率 = 策略收益 ÷ **平均持仓成本** ×100（V5.14 由"平均持仓市值"改为成本口径）：
        // 分母用"实际投进去的钱"（成本基础不随行情虚增），因此上涨行情里不会像市值口径那样被抬高、
        // 与"平均仓位未满时收益率应高于总收益率"的直觉一致；恒等式：总收益率 = 本指标 × (平均成本 ÷ 初始资金)。
        // 从未持仓（均值 0）时为 null
        BigDecimal positionReturnPct = avgPositionCost.compareTo(BigDecimal.ZERO) <= 0 ? null
                : finalAssets.subtract(initialCapital).multiply(BigDecimal.valueOf(100))
                        .divide(avgPositionCost, 4, RoundingMode.HALF_UP);
        return new BacktestResult(trades, equityCurve, avgPositionShare, avgPositionValue, avgPositionCost,
                positionReturnPct, drawdownCurve, benchmarkCurve, finalAssets,
                state.getSellCount(), state.getWinCount());
    }

    private void executeAction(BacktestAction action, BigDecimal price, int execIndex, boolean etf,
                               LocalDate tradeDate, BacktestState state, List<BacktestTradeDetail> trades) {
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        if (BacktestAction.BUY.equals(action.type())) {
            executeBuy(action, price, execIndex, etf, tradeDate, state, trades);
        } else {
            executeSell(action, price, execIndex, etf, tradeDate, state, trades);
        }
    }

    private void executeBuy(BacktestAction action, BigDecimal price, int execIndex, boolean etf,
                            LocalDate tradeDate, BacktestState state, List<BacktestTradeDetail> trades) {
        BigDecimal share = action.share().setScale(2, RoundingMode.DOWN);
        share = share.min(state.getCash().divide(price, 2, RoundingMode.DOWN));
        // 现金不足（含费用）时按 1% 步进折算
        int guard = 0;
        while (share.compareTo(BigDecimal.ZERO) > 0 && guard++ < 200) {
            BigDecimal amount = share.multiply(price).setScale(2, RoundingMode.HALF_UP);
            BigDecimal fee = buyFee(amount, etf);
            if (amount.add(fee).compareTo(state.getCash()) <= 0) {
                BigDecimal oldShares = state.getShares();
                BigDecimal newShares = oldShares.add(share);
                state.setAvgCost(newShares.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO
                        : oldShares.multiply(state.getAvgCost()).add(amount).add(fee)
                                .divide(newShares, 6, RoundingMode.HALF_UP));
                state.setCash(state.getCash().subtract(amount).subtract(fee));
                state.setShares(newShares);
                state.setLastBuyIndex(execIndex);
                addTrade(trades, tradeDate, "BUY", price, share, amount, fee, state, action.reason());
                return;
            }
            share = share.multiply(BigDecimal.valueOf(0.99)).setScale(2, RoundingMode.DOWN);
        }
    }

    private void executeSell(BacktestAction action, BigDecimal price, int execIndex, boolean etf,
                             LocalDate tradeDate, BacktestState state, List<BacktestTradeDetail> trades) {
        if (state.getLastBuyIndex() == execIndex) {
            return;
        }
        BigDecimal share = state.getShares().min(action.share().setScale(2, RoundingMode.DOWN));
        if (share.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal amount = share.multiply(price).setScale(2, RoundingMode.HALF_UP);
        BigDecimal fee = sellFee(amount, etf);
        BigDecimal profit = amount.subtract(fee)
                .subtract(state.getAvgCost().multiply(sellableCost(state, share)))
                .setScale(2, RoundingMode.HALF_UP);
        state.setCash(state.getCash().add(amount).subtract(fee));
        state.setShares(state.getShares().subtract(share));
        state.setRealizedPnl(state.getRealizedPnl().add(profit));
        state.setSellCount(state.getSellCount() + 1);
        if (profit.compareTo(BigDecimal.ZERO) > 0) {
            state.setWinCount(state.getWinCount() + 1);
        }
        if (state.getShares().compareTo(BigDecimal.ZERO) == 0) {
            state.setAvgCost(BigDecimal.ZERO);
        }
        addTrade(trades, tradeDate, "SELL", price, share, amount, fee, state, action.reason());
    }

    /** 卖出份额的成本计算基数（份额精度对齐） */
    private BigDecimal sellableCost(BacktestState state, BigDecimal share) {
        return share.setScale(6, RoundingMode.HALF_UP);
    }

    private void addTrade(List<BacktestTradeDetail> trades, LocalDate tradeDate, String direction,
                          BigDecimal price, BigDecimal share, BigDecimal amount, BigDecimal fee,
                          BacktestState state, String reason) {
        BacktestTradeDetail detail = new BacktestTradeDetail();
        detail.setTradeDate(tradeDate);
        detail.setDirection(direction);
        detail.setPrice(price);
        detail.setShare(share);
        detail.setAmount(amount);
        detail.setFee(fee);
        detail.setCashAfter(state.getCash().setScale(2, RoundingMode.HALF_UP));
        detail.setPositionAfter(state.getShares().setScale(2, RoundingMode.HALF_UP));
        detail.setReason(reason);
        trades.add(detail);
    }

    private BigDecimal buyFee(BigDecimal amount, boolean etf) {
        if (etf) {
            return BigDecimal.valueOf(Math.max(amount.doubleValue() * feeProperties.getEtfCommission(),
                    feeProperties.getEtfMinCommission())).setScale(2, RoundingMode.HALF_UP);
        }
        return amount.multiply(BigDecimal.valueOf(feeProperties.getOtcPurchase())).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal sellFee(BigDecimal amount, boolean etf) {
        if (etf) {
            return BigDecimal.valueOf(Math.max(amount.doubleValue() * feeProperties.getEtfCommission(),
                    feeProperties.getEtfMinCommission())).setScale(2, RoundingMode.HALF_UP);
        }
        return amount.multiply(BigDecimal.valueOf(feeProperties.getOtcRedeem())).setScale(2, RoundingMode.HALF_UP);
    }
}
