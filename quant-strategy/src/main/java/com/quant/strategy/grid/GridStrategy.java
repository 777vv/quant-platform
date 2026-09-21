package com.quant.strategy.grid;

import java.math.BigDecimal;
import java.math.RoundingMode;

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
 * 网格交易策略（技术文档 6.4 参数模型）：
 * 价格每下穿一格买入 sharePerGrid 份、每上穿一格卖出一份；突破上沿清仓、跌破下沿观望。
 * 回测中锚点价随信号同步移动一格（等差/等比）。
 */
@Component
public class GridStrategy implements Strategy {

    public static final String TYPE = "GRID";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String name() {
        return "网格交易";
    }

    @Override
    public void validateParams(JsonNode params) {
        if (!params.has("upper") || !params.has("lower") || !params.has("grids") || !params.has("sharePerGrid")) {
            throw new BizException("网格参数缺失（需 upper/lower/grids/sharePerGrid）");
        }
        BigDecimal upper = Strategy.dec(params, "upper", BigDecimal.ZERO);
        BigDecimal lower = Strategy.dec(params, "lower", BigDecimal.ZERO);
        if (upper.compareTo(lower) <= 0 || lower.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("网格上下沿须满足 0 < lower < upper");
        }
        if (Strategy.intOr(params, "grids", 10) < 2) {
            throw new BizException("格数至少为 2");
        }
        if (Strategy.dec(params, "sharePerGrid", BigDecimal.ZERO).compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("每格份额须大于 0");
        }
        String mode = Strategy.strOr(params, "mode", "arithmetic");
        if (!"arithmetic".equals(mode) && !"geometric".equals(mode)) {
            throw new BizException("mode 仅支持 arithmetic/geometric");
        }
    }

    @Override
    public Signal generateSignal(StrategyContext context) {
        MarketDataSeries series = context.recentSeries();
        if (series.size() == 0) {
            return new Signal(Signal.HOLD, null, "无行情数据");
        }
        BigDecimal price = series.get(series.size() - 1).close();
        JsonNode params = context.params();
        BigDecimal upper = Strategy.dec(params, "upper", BigDecimal.ZERO);
        BigDecimal lower = Strategy.dec(params, "lower", BigDecimal.ZERO);
        BigDecimal anchor = anchorOf(params, series);
        Step step = stepOf(params);
        if (price.compareTo(upper) > 0) {
            return new Signal(Signal.SELL, price, "现价突破网格上沿 " + upper + "，建议清仓离场");
        }
        if (price.compareTo(lower) < 0) {
            return new Signal(Signal.HOLD, price, "现价跌破网格下沿 " + lower + "，区间外观望");
        }
        if (price.compareTo(step.down(anchor)) <= 0) {
            return new Signal(Signal.BUY, price, "现价 " + price + " 较锚点 " + anchor + " 下穿一格，建议买入 "
                    + Strategy.dec(params, "sharePerGrid", BigDecimal.ZERO) + " 份");
        }
        if (price.compareTo(step.up(anchor)) >= 0) {
            return new Signal(Signal.SELL, price, "现价 " + price + " 较锚点 " + anchor + " 上穿一格，建议卖出 "
                    + Strategy.dec(params, "sharePerGrid", BigDecimal.ZERO) + " 份");
        }
        return new Signal(Signal.HOLD, price, "现价处于网格内（锚点 " + anchor + "），继续持有等待触格");
    }

    @Override
    public BacktestAction decide(int index, MarketDataSeries data, BacktestState state) {
        JsonNode params = (JsonNode) state.getScratch().get("params");
        BigDecimal price = data.get(index).close();
        BigDecimal upper = Strategy.dec(params, "upper", BigDecimal.ZERO);
        BigDecimal lower = Strategy.dec(params, "lower", BigDecimal.ZERO);
        BigDecimal sharePerGrid = Strategy.dec(params, "sharePerGrid", BigDecimal.ZERO);
        Step step = stepOf(params);
        // 底仓（仅回测首次）
        BigDecimal base = Strategy.dec(params, "basePosition", BigDecimal.ZERO);
        if (Boolean.TRUE != state.getScratch().get("baseDone") && base.compareTo(BigDecimal.ZERO) > 0) {
            state.getScratch().put("baseDone", Boolean.TRUE);
            return BacktestAction.buy(base, "建立底仓 " + base + " 份");
        }
        if (price.compareTo(upper) > 0) {
            return state.getShares().compareTo(BigDecimal.ZERO) > 0
                    ? BacktestAction.sell(state.getShares(), "突破网格上沿 " + upper + "，清仓")
                    : BacktestAction.hold();
        }
        if (price.compareTo(lower) < 0) {
            return BacktestAction.hold();
        }
        BigDecimal anchor = anchorOfScratch(state, params, data);
        boolean movedDown = false;
        boolean movedUp = false;
        while (price.compareTo(step.down(anchor)) <= 0) {
            anchor = step.down(anchor);
            movedDown = true;
        }
        while (price.compareTo(step.up(anchor)) >= 0) {
            anchor = step.up(anchor);
            movedUp = true;
        }
        state.getScratch().put("anchor", anchor);
        if (movedDown) {
            return BacktestAction.buy(sharePerGrid, "网格下穿买入（锚点→" + anchor + "）");
        }
        if (movedUp) {
            return BacktestAction.sell(sharePerGrid, "网格上穿卖出（锚点→" + anchor + "）");
        }
        return BacktestAction.hold();
    }

    /** 锚点取值：配置 anchorPrice 优先；否则默认取窗口首日收盘价并夹回网格区间 */
    private BigDecimal anchorOf(JsonNode params, MarketDataSeries series) {
        return defaultAnchor(params, series);
    }

    /**
     * 默认锚点计算（public 供信号任务首跑后回写 anchorPrice，保证后续每日信号稳定可复现）。
     */
    public static BigDecimal defaultAnchor(JsonNode params, MarketDataSeries series) {
        BigDecimal configured = Strategy.dec(params, "anchorPrice", BigDecimal.ZERO);
        if (configured.compareTo(BigDecimal.ZERO) > 0) {
            return configured;
        }
        if (series.size() == 0) {
            return Strategy.dec(params, "lower", BigDecimal.ZERO);
        }
        BigDecimal first = series.get(0).close();
        BigDecimal upper = Strategy.dec(params, "upper", BigDecimal.ZERO);
        BigDecimal lower = Strategy.dec(params, "lower", BigDecimal.ZERO);
        return first.max(lower).min(upper);
    }

    private BigDecimal anchorOfScratch(BacktestState state, JsonNode params, MarketDataSeries data) {
        BigDecimal anchor = (BigDecimal) state.getScratch().get("anchor");
        if (anchor == null) {
            anchor = anchorOf(params, data);
            state.getScratch().put("anchor", anchor);
        }
        return anchor;
    }

    private Step stepOf(JsonNode params) {
        BigDecimal upper = Strategy.dec(params, "upper", BigDecimal.ZERO);
        BigDecimal lower = Strategy.dec(params, "lower", BigDecimal.ZERO);
        int grids = Strategy.intOr(params, "grids", 10);
        if ("geometric".equals(Strategy.strOr(params, "mode", "arithmetic"))) {
            BigDecimal ratio = BigDecimal.valueOf(Math.pow(upper.doubleValue() / lower.doubleValue(), 1.0 / grids))
                    .setScale(8, RoundingMode.HALF_UP);
            return new Step() {
                @Override
                public BigDecimal up(BigDecimal anchor) {
                    return anchor.multiply(ratio).setScale(4, RoundingMode.HALF_UP);
                }

                @Override
                public BigDecimal down(BigDecimal anchor) {
                    return anchor.divide(ratio, 4, RoundingMode.HALF_UP);
                }
            };
        }
        BigDecimal step = upper.subtract(lower).divide(BigDecimal.valueOf(grids), 4, RoundingMode.HALF_UP);
        return new Step() {
            @Override
            public BigDecimal up(BigDecimal anchor) {
                return anchor.add(step);
            }

            @Override
            public BigDecimal down(BigDecimal anchor) {
                return anchor.subtract(step);
            }
        };
    }

    private interface Step {

        BigDecimal up(BigDecimal anchor);

        BigDecimal down(BigDecimal anchor);
    }
}
