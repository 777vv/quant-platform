package com.quant.strategy.valuation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import com.quant.common.exception.BizException;
import com.quant.strategy.core.BacktestAction;
import com.quant.strategy.core.BacktestState;
import com.quant.strategy.core.MarketDataSeries;
import com.quant.strategy.core.Signal;
import com.quant.strategy.core.Strategy;
import com.quant.strategy.core.StrategyContext;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;

/**
 * 估值百分位策略（技术文档 6.4 参数模型）：
 * 以跟踪指数近 windowYears 年 PE 百分位分档——
 * p<=lowPct 时按档位加仓至 底仓+k*每档份额（k=1..steps 越低档越重）；
 * p>=highPct 时按档位减仓至 底仓*(1-k/steps)；区间内维持。
 */
@Component
public class ValPercentileStrategy implements Strategy {

    public static final String TYPE = "VAL_PERCENTILE";

    private static final int MIN_SAMPLES = 30;

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String name() {
        return "估值百分位";
    }

    @Override
    public void validateParams(JsonNode params) {
        double lowPct = Strategy.dblOr(params, "lowPct", 20);
        double highPct = Strategy.dblOr(params, "highPct", 80);
        if (lowPct <= 0 || highPct >= 100 || lowPct >= highPct) {
            throw new BizException("阈值须满足 0 < lowPct < highPct < 100");
        }
        if (Strategy.intOr(params, "steps", 5) < 1 || Strategy.intOr(params, "steps", 5) > 10) {
            throw new BizException("分档数 steps 须在 1-10");
        }
        if (Strategy.intOr(params, "windowYears", 10) < 1 || Strategy.intOr(params, "windowYears", 10) > 30) {
            throw new BizException("回看窗口须在 1-30 年");
        }
        if (Strategy.dec(params, "sharePerStep", BigDecimal.ZERO).compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("每档份额须大于 0");
        }
    }

    @Override
    public Signal generateSignal(StrategyContext context) {
        MarketDataSeries series = context.recentSeries();
        if (series.size() == 0) {
            return new Signal(Signal.HOLD, null, "无行情数据");
        }
        int last = series.size() - 1;
        JsonNode params = context.params();
        BigDecimal price = series.get(last).close();
        Double percentile = percentileAt(series, last, Strategy.intOr(params, "windowYears", 10));
        if (percentile == null) {
            return new Signal(Signal.HOLD, price, "跟踪指数估值数据不足，暂不给出建议");
        }
        double lowPct = Strategy.dblOr(params, "lowPct", 20);
        double highPct = Strategy.dblOr(params, "highPct", 80);
        BigDecimal pe = series.get(last).pe();
        if (percentile <= lowPct) {
            int k = level(percentile, lowPct, Strategy.intOr(params, "steps", 5), true);
            return new Signal(Signal.BUY, price, "指数PE " + pe + "，近" + Strategy.intOr(params, "windowYears", 10)
                    + "年百分位 " + String.format("%.1f", percentile) + "%（低估第" + k + "档），建议加仓 "
                    + Strategy.dec(params, "sharePerStep", BigDecimal.ZERO) + " 份");
        }
        if (percentile >= highPct) {
            int k = level(percentile, highPct, Strategy.intOr(params, "steps", 5), false);
            return new Signal(Signal.SELL, price, "指数PE " + pe + "，近" + Strategy.intOr(params, "windowYears", 10)
                    + "年百分位 " + String.format("%.1f", percentile) + "%（高估第" + k + "档），建议减仓");
        }
        return new Signal(Signal.HOLD, price, "指数PE " + pe + "，百分位 " + String.format("%.1f", percentile)
                + "%，处于合理区间，继续持有");
    }

    @Override
    public BacktestAction decide(int index, MarketDataSeries data, BacktestState state) {
        JsonNode params = (JsonNode) state.getScratch().get("params");
        BigDecimal base = Strategy.dec(params, "basePosition", BigDecimal.ZERO);
        if (Boolean.TRUE != state.getScratch().get("baseDone") && base.compareTo(BigDecimal.ZERO) > 0) {
            state.getScratch().put("baseDone", Boolean.TRUE);
            return BacktestAction.buy(base, "建立底仓 " + base + " 份");
        }
        Double percentile = percentileAt(data, index, Strategy.intOr(params, "windowYears", 10));
        if (percentile == null) {
            return BacktestAction.hold();
        }
        double lowPct = Strategy.dblOr(params, "lowPct", 20);
        double highPct = Strategy.dblOr(params, "highPct", 80);
        int steps = Strategy.intOr(params, "steps", 5);
        BigDecimal sharePerStep = Strategy.dec(params, "sharePerStep", BigDecimal.ZERO);
        BigDecimal held = state.getShares();
        BigDecimal tolerance = sharePerStep.multiply(BigDecimal.valueOf(0.5));
        if (percentile <= lowPct) {
            int k = level(percentile, lowPct, steps, true);
            BigDecimal target = base.add(sharePerStep.multiply(BigDecimal.valueOf(k)));
            if (held.compareTo(target.subtract(tolerance)) < 0) {
                BigDecimal diff = target.subtract(held).max(BigDecimal.ZERO);
                return BacktestAction.buy(diff, "低估加仓至第" + k + "档（百分位"
                        + String.format("%.1f", percentile) + "%）");
            }
        } else if (percentile >= highPct) {
            int k = level(percentile, highPct, steps, false);
            BigDecimal target = base.multiply(BigDecimal.ONE.subtract(
                    BigDecimal.valueOf(k).divide(BigDecimal.valueOf(steps), 6, RoundingMode.HALF_UP)));
            if (held.compareTo(target.add(tolerance)) > 0) {
                BigDecimal diff = held.subtract(target).max(BigDecimal.ZERO);
                return BacktestAction.sell(diff, "高估减仓至" + k + "/" + steps + " 档（百分位"
                        + String.format("%.1f", percentile) + "%）");
            }
        }
        return BacktestAction.hold();
    }

    /** 低估档位：p 从 lowPct 向 0 均分 steps 档；高估档位：p 从 highPct 向 100 均分 */
    private int level(double percentile, double threshold, int steps, boolean lowSide) {
        double span = lowSide ? threshold : 100 - threshold;
        double offset = lowSide ? threshold - percentile : percentile - threshold;
        int k = (int) Math.ceil(offset / (span / steps));
        return Math.max(1, Math.min(steps, k));
    }

    /** 截至 index 日的窗口内 PE 百分位（样本不足返回 null） */
    private Double percentileAt(MarketDataSeries data, int index, int windowYears) {
        BigDecimal current = data.get(index).pe();
        if (current == null) {
            return null;
        }
        LocalDate windowStart = data.get(index).date().minusYears(windowYears);
        int count = 0;
        int notGreater = 0;
        for (int j = index; j >= 0 && data.get(j).date().compareTo(windowStart) >= 0; j--) {
            BigDecimal pe = data.get(j).pe();
            if (pe != null) {
                count++;
                if (pe.compareTo(current) <= 0) {
                    notGreater++;
                }
            }
        }
        return count < MIN_SAMPLES ? null : notGreater * 100.0 / count;
    }
}
