package com.quant.strategy.ma;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import com.quant.common.exception.BizException;
import com.quant.strategy.core.BacktestAction;
import com.quant.strategy.core.BacktestState;
import com.quant.strategy.core.FeeProperties;
import com.quant.strategy.core.MarketDataSeries;
import com.quant.strategy.core.Signal;
import com.quant.strategy.core.Strategy;
import com.quant.strategy.core.StrategyContext;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;

/**
 * 双均线右侧策略（DUAL_MA，V6.32 用户新增；V6.34 更名，原"双均线"）：快线、慢线的**相对位置**决定目标仓位——
 * 快线值 {@code >} 慢线值（多头排列）→ **全仓份额**；快线值 {@code <} 慢线值（空头排列）→ **底仓份额**；
 * 两值相等（罕见）→ 维持现仓位不动。⚠️ 类型码仍是 `DUAL_MA`（改名只动显示名，历史配置/回测记录不受影响）。
 *
 * <h3>参数（5 个）</h3>
 * <ul>
 *   <li>{@code fullShare} 满仓份额：快线在上时的目标仓位（份），须 {@code > 0}；</li>
 *   <li>{@code baseShare} 底仓份额：快线在下时的目标仓位（份），须 {@code >= 0} 且小于满仓份额；</li>
 *   <li>{@code fastMaDays} 快线均线（日，默认 30）与 {@code slowMaDays} 慢线均线（日，默认 180）：
 *       收盘价的简单移动平均（含当日），取值 2~500；**须快线周期小于慢线周期**；</li>
 *   <li>{@code cooldownDays} 冷静天数：最近一次**实际交易**后的 N 个交易日内不调仓（0=不冷静，0~500）。</li>
 * </ul>
 *
 * <h3>执行口径（目标仓位模型，与均线突破 MA_BREAK 同一套引擎语义）</h3>
 * <ul>
 *   <li>每天**收盘后**按两条均线的相对位置算出目标仓位（快线在上=全仓 / 在下=底仓），
 *       实际仓位 ≠ 目标 → **次日开盘价一次性调仓**到目标；已在目标 → 不动；</li>
 *   <li>状态制（不是"穿越动作"制）：信号不会丢，调仓频率完全由冷静期控制；</li>
 *   <li>**没有「初始仓位份额」参数**（用户口径）：回测首日即按当日两线状态对齐到目标仓位——
 *       快线在上=直接建满仓、在下=只留底仓；</li>
 *   <li>均线样本不足（预热不够）时不判定，维持现仓位。</li>
 * </ul>
 */
@Component
public class DualMaStrategy implements Strategy {

    /** 策略类型标识 */
    public static final String TYPE = "DUAL_MA";

    /** 回测发起前资金校验的费用缓冲（买入约万 2.5 + 滑点余量，取 1%） */
    private static final BigDecimal FEE_BUFFER = new BigDecimal("1.01");

    /** 费率配置（ETF 最低佣金用于判断"补买是否注定无法成交"） */
    private final FeeProperties feeProperties;

    public DualMaStrategy(FeeProperties feeProperties) {
        this.feeProperties = feeProperties;
    }

    /** 参数名：快线均线（日） */
    private static final String P_FAST_MA = "fastMaDays";

    /** 参数名：慢线均线（日） */
    private static final String P_SLOW_MA = "slowMaDays";

    /** 参数名：冷静天数（最近一次交易后 N 个交易日内不重复交易，0=关闭） */
    private static final String P_COOLDOWN_DAYS = "cooldownDays";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String name() {
        return "双均线右侧";
    }

    @Override
    public void validateParams(JsonNode params) {
        BigDecimal full = Strategy.dec(params, "fullShare", BigDecimal.ZERO);
        BigDecimal base = Strategy.dec(params, "baseShare", BigDecimal.ZERO);
        if (full.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("满仓份额须大于 0");
        }
        if (base.compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException("底仓份额不能为负");
        }
        if (base.compareTo(full) >= 0) {
            throw new BizException("底仓份额须小于满仓份额");
        }
        checkMaDays(params, P_FAST_MA, "快线");
        checkMaDays(params, P_SLOW_MA, "慢线");
        if (Strategy.intOr(params, P_FAST_MA, 30) >= Strategy.intOr(params, P_SLOW_MA, 180)) {
            throw new BizException("快线均线周期须小于慢线均线周期（当前 "
                    + Strategy.intOr(params, P_FAST_MA, 30) + " / " + Strategy.intOr(params, P_SLOW_MA, 180) + "）");
        }
        int cooldown = Strategy.intOr(params, P_COOLDOWN_DAYS, 0);
        if (cooldown < 0 || cooldown > 500) {
            throw new BizException("冷静天数须在 0~500 之间（0=不冷静），当前 " + cooldown);
        }
    }

    @Override
    public Signal generateSignal(StrategyContext context) {
        MarketDataSeries series = context.recentSeries();
        if (series.size() < 3) {
            return new Signal(Signal.HOLD, null, "行情数据不足，暂无法判断");
        }
        JsonNode params = context.params();
        int fastDays = Math.max(Strategy.intOr(params, P_FAST_MA, 30), 2);
        int slowDays = Math.max(Strategy.intOr(params, P_SLOW_MA, 180), 2);
        int cooldown = Math.max(Strategy.intOr(params, P_COOLDOWN_DAYS, 0), 0);
        int index = series.size() - 1;
        BigDecimal price = series.get(index).close();
        LocalDate lastTradeDate = context.lastTradeDate();
        int barsSinceTrade = lastTradeDate == null ? Integer.MAX_VALUE : barsSince(series, lastTradeDate, index);
        BigDecimal fast = maAt(series, index, fastDays);
        BigDecimal slow = maAt(series, index, slowDays);
        if (fast == null || slow == null) {
            return new Signal(Signal.HOLD, price, "历史数据不足，均线信号暂无法判定");
        }
        BigDecimal base = Strategy.dec(params, "baseShare", BigDecimal.ZERO);
        BigDecimal full = Strategy.dec(params, "fullShare", BigDecimal.ZERO);
        // 冷却中：不给操作建议（状态每日重评，冷却期满当天按最新状态给出）
        if (cooldown > 0 && barsSinceTrade < cooldown) {
            return new Signal(Signal.HOLD, price, "冷静期内（距上次实际交易 " + barsSinceTrade
                    + " 个交易日，冷静 " + cooldown + " 天），暂不给操作建议");
        }
        int cmp = fast.compareTo(slow);
        String stateDesc = "快线 " + fastDays + " 日 " + strip(fast) + "，" + (cmp > 0 ? ">" : cmp < 0 ? "<" : "=")
                + " 慢线 " + slowDays + " 日 " + strip(slow);
        if (cmp == 0) {
            return new Signal(Signal.HOLD, price, stateDesc + "（两线相等、状态不明确），维持现仓位");
        }
        BigDecimal current = context.currentShares() == null ? BigDecimal.ZERO : context.currentShares();
        BigDecimal target = cmp > 0 ? full : base;
        String targetName = cmp > 0 ? "全仓份额" : "底仓份额";
        if (target.compareTo(current) == 0) {
            return new Signal(Signal.HOLD, price, stateDesc + "，已处于目标仓位（" + targetName + " "
                    + strip(target) + " 份），无操作建议");
        }
        if (target.compareTo(current) > 0) {
            return new Signal(Signal.BUY, price, stateDesc + " → 目标为" + targetName + "，建议买入至 "
                    + strip(target) + " 份");
        }
        return new Signal(Signal.SELL, price, stateDesc + " → 目标为" + targetName + "，建议卖出至 "
                + strip(target) + " 份");
    }

    @Override
    public BacktestAction decide(int index, MarketDataSeries data, BacktestState state) {
        JsonNode params = (JsonNode) state.getScratch().get("params");
        int fastDays = Math.max(Strategy.intOr(params, P_FAST_MA, 30), 2);
        int slowDays = Math.max(Strategy.intOr(params, P_SLOW_MA, 180), 2);
        int cooldown = Math.max(Strategy.intOr(params, P_COOLDOWN_DAYS, 0), 0);
        Integer lastIndex = (Integer) state.getScratch().get("lastIndex");
        BigDecimal currClose = data.get(index).close();
        if (currClose == null) {
            return BacktestAction.hold();
        }
        BigDecimal fast = maAt(data, index, fastDays);
        BigDecimal slow = maAt(data, index, slowDays);
        if (fast == null || slow == null) {
            return BacktestAction.hold();
        }
        BigDecimal full = Strategy.dec(params, "fullShare", BigDecimal.ZERO);
        BigDecimal base = Strategy.dec(params, "baseShare", BigDecimal.ZERO);
        BigDecimal current = state.getShares();
        // 冷却期内不调仓；状态每日重评，冷却期满当天拿到的就是最新目标
        if (cooldown > 0 && lastIndex != null && index - lastIndex < cooldown) {
            return new BacktestAction(BacktestAction.HOLD, BigDecimal.ZERO, "冷静期内（剩 "
                    + (cooldown - (index - lastIndex)) + " 个交易日），暂不调仓");
        }
        int cmp = fast.compareTo(slow);
        if (cmp == 0) {
            return BacktestAction.hold();
        }
        String stateDesc = "快线 " + fastDays + " 日 " + strip(fast) + "，" + (cmp > 0 ? ">" : "<")
                + " 慢线 " + slowDays + " 日 " + strip(slow);
        BigDecimal target = cmp > 0 ? full : base;
        if (target.compareTo(current) == 0) {
            return BacktestAction.hold();
        }
        if (target.compareTo(current) > 0) {
            // 现金连最低佣金都覆盖不了时这笔补买注定无法成交——不发单、不重置冷却
            BigDecimal minFee = data.isEtf()
                    ? BigDecimal.valueOf(feeProperties.getEtfMinCommission())
                    : BigDecimal.ZERO;
            if (state.getCash().compareTo(minFee) <= 0) {
                return BacktestAction.hold();
            }
            state.getScratch().put("lastIndex", index);
            return BacktestAction.buy(target.subtract(current).setScale(2, RoundingMode.DOWN),
                    stateDesc + "，状态满足买入 → 买入至" + (cmp > 0 ? "全仓" : "底仓") + " " + strip(target) + " 份");
        }
        state.getScratch().put("lastIndex", index);
        return BacktestAction.sell(current.subtract(target).setScale(2, RoundingMode.DOWN),
                stateDesc + "，状态满足卖出 → 卖出至" + (cmp > 0 ? "全仓" : "底仓") + " " + strip(target) + " 份");
    }

    @Override
    public void validateBacktest(MarketDataSeries series, int startIndex, JsonNode params,
                                 BigDecimal initialCapital) {
        if (startIndex < 0 || series.size() <= startIndex) {
            return;
        }
        int fastDays = Math.max(Strategy.intOr(params, P_FAST_MA, 30), 2);
        int slowDays = Math.max(Strategy.intOr(params, P_SLOW_MA, 180), 2);
        int need = Math.max(fastDays, slowDays);
        // 均线周期大于预热历史的硬校验：区间前段均线值不足会让信号推迟
        if (startIndex + 1 < need) {
            throw new BizException("均线周期 " + need + " 个交易日超过了回测区间首日前的预热数据（"
                    + (startIndex + 1) + " 根）：区间前段的均线值不足，双均线信号会推迟。"
                    + "请把起始日期再往前调，或把均线周期调小到 " + (startIndex + 1) + " 以内。");
        }
        BigDecimal full = Strategy.dec(params, "fullShare", BigDecimal.ZERO);
        BigDecimal price = series.get(startIndex).close();
        if (full.compareTo(BigDecimal.ZERO) <= 0 || price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal needed = full.multiply(price).multiply(FEE_BUFFER);
        if (needed.compareTo(initialCapital) > 0) {
            BigDecimal affordable = initialCapital.divide(price.multiply(FEE_BUFFER), 0, RoundingMode.DOWN);
            throw new BizException("初始本金不足：满仓 " + strip(full) + " 份 × 区间首日价 " + strip(price)
                    + " 元 ≈ " + needed.setScale(0, RoundingMode.DOWN) + " 元（含 1% 费用缓冲），"
                    + "大于初始本金 " + strip(initialCapital) + " 元。"
                    + "请把初始本金调到 ≥ " + needed.setScale(0, RoundingMode.UP) + " 元，"
                    + "或把满仓份额改到 " + affordable + " 份以内。");
        }
    }

    /** 简单移动平均：closes [index-n+1 .. index] 的平均（含当日）；样本不足返回 null */
    private BigDecimal maAt(MarketDataSeries series, int index, int n) {
        if (index + 1 < n) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = index - n + 1; i <= index; i++) {
            BigDecimal close = series.get(i).close();
            if (close == null) {
                return null;
            }
            sum = sum.add(close);
        }
        return sum.divide(BigDecimal.valueOf(n), 4, RoundingMode.HALF_UP);
    }

    /** 交易日所在 bar 下标（该日或其后第一根）；早于序列起点返回 0，找不到返回 -1 */
    private int barIndexOnOrAfter(MarketDataSeries series, LocalDate date, int index) {
        for (int i = 0; i <= index; i++) {
            if (!series.get(i).date().isBefore(date)) {
                return i;
            }
        }
        return -1;
    }

    /** 距某日的交易日 bar 数；该日早于加载窗口时返回 Integer.MAX_VALUE（视为无冷却） */
    private int barsSince(MarketDataSeries series, LocalDate date, int index) {
        int at = barIndexOnOrAfter(series, date, index);
        return at < 0 ? Integer.MAX_VALUE : index - at;
    }

    /** 均线周期校验：2~500 个交易日 */
    private void checkMaDays(JsonNode params, String field, String label) {
        int days = Strategy.intOr(params, field, 0);
        if (days < 2 || days > 500) {
            throw new BizException(label + "均线周期须在 2~500 之间（交易日），当前 " + days);
        }
    }

    /** 去掉无意义的小数零，便于文案阅读 */
    private String strip(BigDecimal value) {
        return value == null ? "无" : value.stripTrailingZeros().toPlainString();
    }
}
