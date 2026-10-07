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
 * 均线突破策略（MA_BREAK，V5.73 引入、V5.75 支持方向配置）。趋势/均值回归双玩法：
 * 收盘价上穿「均线突破」均线 → 执行突破方向的操作；收盘价下穿「均线跌破」均线 → 执行跌破方向的操作。
 *
 * <h3>参数（7 个）</h3>
 * <ul>
 *   <li>{@code initialShare} 初始仓位份额：仅回测首日一次性建仓（与震荡向上同语义），0 = 不建仓；</li>
 *   <li>{@code baseShare} 底仓份额：卖出操作后的持仓下限；</li>
 *   <li>{@code fullShare} 满仓份额：买入操作后的持仓上限；</li>
 *   <li>{@code breakoutMaDays} 均线突破（日）+ {@code breakoutAction} 突破方向（BUY=买入至满仓 /
 *       SELL=卖出至底仓）；</li>
 *   <li>{@code breakdownMaDays} 均线跌破（日）+ {@code breakdownAction} 跌破方向——
 *       **恒与突破方向相反**（V5.75 用户口径：一买一卖，禁止同向）；</li>
 *   <li>{@code cooldownDays} 冷静天数：最近一次实际交易后的 N 个交易日内不重复交易（0=不冷静）。
 *       冷却期内触发的信号**暂挂不丢**——冷却期满首日按当日均线状态复核：仍满足才执行，
 *       已恢复则暂挂作废、保持仓位不动（V5.74 用户口径）。</li>
 * </ul>
 *
 * <h3>触发判定（V5.87 起：目标仓位模型，全状态驱动）</h3>
 * <ul>
 *   <li>每天收盘按<b>状态</b>（而非穿越动作）计算目标仓位：
 *       收盘在「均线突破」线<b>上方</b> → 执行突破操作（配置买入=满仓 / 卖出=底仓）；
 *       收盘在「均线跌破」线<b>下方</b> → 执行跌破操作（恒与突破操作相反）；</li>
 *   <li><b>双线同触、快线优先</b>：价格夹在两线之间时两侧同时触发，按<b>周期较小的快线</b>执行——
 *       快线反应更快，代表最新状态；</li>
 *   <li><b>中性区</b>：收盘低于突破线又高于跌破线（下降趋势形态的两线之间）方向不明 → 维持现仓位；</li>
 *   <li>实际仓位 ≠ 目标仓位 → 次日开盘价一次性调仓到目标；已在目标仓位 → 不动。
 *       状态制下不存在"信号丢失"，调仓频率完全由冷静期控制；</li>
 *   <li><b>冷静期</b>：最近一次实际交易后 N 个交易日内不调仓（0=关闭）。状态每日重评，
 *       冷却期满当天按最新状态执行——无需暂挂/复核机制（V5.86 及之前的穿越/暂挂模型已废弃）；</li>
 *   <li>均线样本不足（如 250 日均线需要 250 根历史）时不判定，维持现仓位。</li>
 * </ul>
 */
@Component
public class MaBreakStrategy implements Strategy {

    /** 策略类型标识 */
    public static final String TYPE = "MA_BREAK";

    /** 回测发起前资金校验的费用缓冲（买入约万 2.5 + 滑点余量，取 1%） */
    private static final BigDecimal FEE_BUFFER = new BigDecimal("1.01");

    /** 费率配置（ETF 最低佣金用于判断"补买是否注定无法成交"） */
    private final FeeProperties feeProperties;

    public MaBreakStrategy(FeeProperties feeProperties) {
        this.feeProperties = feeProperties;
    }

    /** 参数名：均线突破（日） */
    private static final String P_BREAKOUT_MA = "breakoutMaDays";

    /** 参数名：均线跌破（日） */
    private static final String P_BREAKDOWN_MA = "breakdownMaDays";

    /** 参数名：突破方向（BUY=买入至满仓 / SELL=卖出至底仓） */
    private static final String P_BREAKOUT_ACTION = "breakoutAction";

    /** 参数名：跌破方向（恒与突破方向相反） */
    private static final String P_BREAKDOWN_ACTION = "breakdownAction";

    /** 参数名：冷静天数（最近一次交易后 N 个交易日内不重复交易，0=关闭） */
    private static final String P_COOLDOWN_DAYS = "cooldownDays";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String name() {
        return "均线突破";
    }

    /** 突破方向（BUY=上穿买入至满仓 / SELL=上穿卖出至底仓；缺省 BUY） */
    private String breakoutActionOf(JsonNode params) {
        return Strategy.strOr(params, P_BREAKOUT_ACTION, "BUY");
    }

    /** 跌破方向恒与突破方向相反 */
    private String breakdownActionOf(String breakoutAction) {
        return "BUY".equals(breakoutAction) ? "SELL" : "BUY";
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
        BigDecimal initial = Strategy.dec(params, "initialShare", BigDecimal.ZERO);
        if (initial.compareTo(BigDecimal.ZERO) < 0 || initial.compareTo(full) > 0) {
            throw new BizException("初始仓位份额须在 0 ~ 满仓份额之间，当前 " + initial);
        }
        checkMaDays(params, P_BREAKOUT_MA, "均线突破");
        checkMaDays(params, P_BREAKDOWN_MA, "均线跌破");
        int cooldown = Strategy.intOr(params, P_COOLDOWN_DAYS, 0);
        if (cooldown < 0 || cooldown > 500) {
            throw new BizException("冷静天数须在 0~500 之间（0=不冷静），当前 " + cooldown);
        }
        String breakoutAction = Strategy.strOr(params, P_BREAKOUT_ACTION, "BUY");
        String breakdownAction = Strategy.strOr(params, P_BREAKDOWN_ACTION, "SELL");
        if (!"BUY".equals(breakoutAction) && !"SELL".equals(breakoutAction)) {
            throw new BizException("突破方向只能是 BUY 或 SELL，当前 " + breakoutAction);
        }
        if (!"BUY".equals(breakdownAction) && !"SELL".equals(breakdownAction)) {
            throw new BizException("跌破方向只能是 BUY 或 SELL，当前 " + breakdownAction);
        }
        if (breakoutAction.equals(breakdownAction)) {
            throw new BizException("突破方向与跌破方向必须相反（一买一卖）");
        }
    }

    @Override
    public Signal generateSignal(StrategyContext context) {
        MarketDataSeries series = context.recentSeries();
        // 需要当日收盘 + 均线窗口历史：样本不足先 HOLD（数据每天积累，够了自然出信号）
        if (series.size() < 3) {
            return new Signal(Signal.HOLD, null, "行情数据不足，暂无法判断");
        }
        JsonNode params = context.params();
        int breakout = Math.max(Strategy.intOr(params, P_BREAKOUT_MA, 60), 2);
        int breakdown = Math.max(Strategy.intOr(params, P_BREAKDOWN_MA, 30), 2);
        int cooldown = Math.max(Strategy.intOr(params, P_COOLDOWN_DAYS, 0), 0);
        int index = series.size() - 1;
        BigDecimal price = series.get(index).close();
        LocalDate lastTradeDate = context.lastTradeDate();
        int barsSinceTrade = lastTradeDate == null ? Integer.MAX_VALUE : barsSince(series, lastTradeDate, index);
        BigDecimal maBreakCurr = maAt(series, index, breakout);
        BigDecimal maDownCurr = maAt(series, index, breakdown);
        if (maBreakCurr == null || maDownCurr == null) {
            return new Signal(Signal.HOLD, price, "历史数据不足，均线信号暂无法判定");
        }
        String breakoutAction = breakoutActionOf(params);
        String breakdownAction = breakdownActionOf(breakoutAction);
        BigDecimal current = context.currentShares() == null ? BigDecimal.ZERO : context.currentShares();
        BigDecimal base = Strategy.dec(params, "baseShare", BigDecimal.ZERO);
        BigDecimal full = Strategy.dec(params, "fullShare", BigDecimal.ZERO);
        // 冷却中：不给操作建议（状态每日重评，冷却期满当天按最新状态给出）
        if (cooldown > 0 && barsSinceTrade < cooldown) {
            return new Signal(Signal.HOLD, price, "冷静期内（距上次实际交易 " + barsSinceTrade
                    + " 个交易日，冷静 " + cooldown + " 天），暂不给操作建议");
        }
        // 目标仓位模型（V5.87）：突破线触发（收盘在线上方）→ 突破操作；跌破线触发（收盘在线下方）→ 跌破操作；
        // 两侧同时触发（价格夹在两线之间）→ 快线（周期较小者）优先；两侧都不触发 → 维持现仓位
        boolean breakoutTriggered = price.compareTo(maBreakCurr) > 0;
        boolean breakdownTriggered = price.compareTo(maDownCurr) < 0;
        if (!breakoutTriggered && !breakdownTriggered) {
            return new Signal(Signal.HOLD, price, "价格处于两线之间的中性区（收盘 " + strip(price)
                    + "，" + breakout + "日均线 " + strip(maBreakCurr) + "，" + breakdown
                    + "日均线 " + strip(maDownCurr) + "），维持现仓位");
        }
        boolean bothTriggered = breakoutTriggered && breakdownTriggered;
        boolean useBreakout = bothTriggered ? breakout <= breakdown : breakoutTriggered;
        String directive = useBreakout ? breakoutAction : breakdownAction;
        String triggerDesc = "收盘 " + strip(price) + "（触发线：" + (useBreakout ? "均线突破" : "均线跌破")
                + " " + (useBreakout ? breakout : breakdown) + " 日线 "
                + strip(useBreakout ? maBreakCurr : maDownCurr)
                + (bothTriggered ? "；双线同触、快线优先）" : "）");
        BigDecimal target = "BUY".equals(directive) ? full : base;
        if (target.compareTo(current) > 0) {
            return new Signal(Signal.BUY, price, triggerDesc + "，状态满足买入，建议买入至满仓");
        }
        if (target.compareTo(current) < 0) {
            return new Signal(Signal.SELL, price, triggerDesc + "，状态满足卖出，建议卖出只留底仓 " + strip(base) + " 份");
        }
        return new Signal(Signal.HOLD, price, triggerDesc + "，已处于目标仓位（"
                + ("BUY".equals(directive) ? "满仓" : "底仓") + "），无操作建议");
    }

    @Override
    public BacktestAction decide(int index, MarketDataSeries data, BacktestState state) {
        JsonNode params = (JsonNode) state.getScratch().get("params");
        // 初始仓位（与震荡向上同语义）：回测首日一次性按 initialShare 建仓，仅一次
        if (Boolean.TRUE != state.getScratch().get("initialDone")) {
            state.getScratch().put("initialDone", Boolean.TRUE);
            BigDecimal initial = Strategy.dec(params, "initialShare", BigDecimal.ZERO);
            BigDecimal full = Strategy.dec(params, "fullShare", BigDecimal.ZERO);
            if (initial.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal price = data.get(index).close();
                state.getScratch().put("lastDirection", Signal.BUY);
                state.getScratch().put("lastPrice", price);
                state.getScratch().put("lastIndex", index);
                return BacktestAction.buy(initial.min(full), "建立初始仓位 " + strip(initial.min(full)) + " 份");
            }
        }
        if (index < 1) {
            return BacktestAction.hold();
        }
        int breakout = Math.max(Strategy.intOr(params, P_BREAKOUT_MA, 60), 2);
        int breakdown = Math.max(Strategy.intOr(params, P_BREAKDOWN_MA, 30), 2);
        int cooldown = Math.max(Strategy.intOr(params, P_COOLDOWN_DAYS, 0), 0);
        Integer lastIndex = (Integer) state.getScratch().get("lastIndex");
        BigDecimal currClose = data.get(index).close();
        if (currClose == null) {
            return BacktestAction.hold();
        }
        BigDecimal maBreakCurr = maAt(data, index, breakout);
        BigDecimal maDownCurr = maAt(data, index, breakdown);
        BigDecimal full = Strategy.dec(params, "fullShare", BigDecimal.ZERO);
        BigDecimal base = Strategy.dec(params, "baseShare", BigDecimal.ZERO);
        BigDecimal current = state.getShares();
        String breakoutAction = breakoutActionOf(params);
        String breakdownAction = breakdownActionOf(breakoutAction);
        // 冷却期内不调仓（V5.74 口径：最近一次实际交易后 N 个交易日内不重复交易）；
        // 状态每日重评，冷却期满当天拿到的就是最新目标，无需暂挂/复核机制
        if (cooldown > 0 && lastIndex != null && index - lastIndex < cooldown) {
            return new BacktestAction(BacktestAction.HOLD, BigDecimal.ZERO, "冷静期内（剩 "
                    + (cooldown - (index - lastIndex)) + " 个交易日），暂不调仓");
        }
        // 目标仓位模型（V5.87）：突破线触发（收盘在线上方）→ 突破操作；跌破线触发（收盘在线下方）→ 跌破操作；
        // 两侧同时触发（价格夹在两线之间）→ 快线（周期较小者）优先；两侧都不触发 → 维持现仓位
        boolean breakoutTriggered = maBreakCurr != null && currClose.compareTo(maBreakCurr) > 0;
        boolean breakdownTriggered = maDownCurr != null && currClose.compareTo(maDownCurr) < 0;
        if (!breakoutTriggered && !breakdownTriggered) {
            return BacktestAction.hold();
        }
        boolean bothTriggered = breakoutTriggered && breakdownTriggered;
        boolean useBreakout = bothTriggered ? breakout <= breakdown : breakoutTriggered;
        String directive = useBreakout ? breakoutAction : breakdownAction;
        String triggerDesc = "收盘 " + strip(currClose) + "（触发线：" + (useBreakout ? "均线突破" : "均线跌破")
                + " " + (useBreakout ? breakout : breakdown) + " 日线 "
                + strip(useBreakout ? maBreakCurr : maDownCurr)
                + (bothTriggered ? "；双线同触、快线优先）" : "）");
        BigDecimal target = "BUY".equals(directive) ? full : base;
        if (target.compareTo(current) == 0) {
            return BacktestAction.hold();
        }
        if (target.compareTo(current) > 0) {
            // 现金连最低佣金都覆盖不了时这笔补买注定无法成交——不发单、不重置冷却
            //（否则每个冷静期满都会白耗一次并把 lastIndex 不断后移，把真卖出信号吞在冷却期里，V5.86 实测踩过）
            BigDecimal minFee = data.isEtf()
                    ? BigDecimal.valueOf(feeProperties.getEtfMinCommission())
                    : BigDecimal.ZERO;
            if (state.getCash().compareTo(minFee) <= 0) {
                return BacktestAction.hold();
            }
            state.getScratch().put("lastIndex", index);
            return BacktestAction.buy(target.subtract(current).setScale(2, RoundingMode.DOWN),
                    triggerDesc + "，状态满足买入 → 买入至满仓 " + strip(full) + " 份");
        }
        state.getScratch().put("lastIndex", index);
        return BacktestAction.sell(current.subtract(target).setScale(2, RoundingMode.DOWN),
                triggerDesc + "，状态满足卖出 → 卖出只留底仓 " + strip(base) + " 份");
    }

    @Override
    public void validateBacktest(MarketDataSeries series, int startIndex, JsonNode params,
                                 BigDecimal initialCapital) {
        if (startIndex < 0 || series.size() <= startIndex) {
            return;
        }
        int breakout = Math.max(Strategy.intOr(params, P_BREAKOUT_MA, 60), 2);
        int breakdown = Math.max(Strategy.intOr(params, P_BREAKDOWN_MA, 30), 2);
        int need = Math.max(breakout, breakdown);
        // 均线周期大于预热历史的硬校验：区间前段均线值不足会让突破/跌破信号推迟
        if (startIndex + 1 < need) {
            throw new BizException("均线周期 " + need + " 个交易日超过了回测区间首日前的预热数据（"
                    + (startIndex + 1) + " 根）：区间前段的均线值不足，突破/跌破信号会推迟。"
                    + "请把起始日期再往前调，或把均线周期调小到 " + (startIndex + 1) + " 以内。");
        }
        BigDecimal full = Strategy.dec(params, "fullShare", BigDecimal.ZERO);
        BigDecimal price = series.get(startIndex).close();
        if (full.compareTo(BigDecimal.ZERO) <= 0 || price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal need2 = full.multiply(price).multiply(FEE_BUFFER);
        if (need2.compareTo(initialCapital) > 0) {
            BigDecimal affordable = initialCapital.divide(price.multiply(FEE_BUFFER), 0, RoundingMode.DOWN);
            throw new BizException("初始本金不足：满仓 " + strip(full) + " 份 × 区间首日价 " + strip(price)
                    + " 元 ≈ " + need2.setScale(0, RoundingMode.DOWN) + " 元（含 1% 费用缓冲），"
                    + "大于初始本金 " + strip(initialCapital) + " 元。"
                    + "请把初始本金调到 ≥ " + need2.setScale(0, RoundingMode.UP) + " 元，"
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
