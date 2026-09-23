package com.quant.strategy.oscillation;

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
 * 震荡向上策略（OSC_UP）。V5.13 重构：**去掉"首次/二次涨跌"状态机与档位目标**，
 * 改为「固定份额 + 区间极值触发」。口径均经用户逐条确认（docs/02 V5.4→V5.13）：
 *
 * <h3>参数（7 个）</h3>
 * <ul>
 *   <li>{@code baseShare} 底仓份额：卖出下限，建议卖出后持仓不低于它；</li>
 *   <li>{@code fullShare} 满仓份额：买入上限，建议买入后持仓不超过它；</li>
 *   <li>{@code windowDays} K线天数：向前回看的历史数据根数（也是"最多往前"的封顶）；</li>
 *   <li>{@code riseReducePct} 上涨减仓%：涨幅超阈值 → 卖出；</li>
 *   <li>{@code fallAddPct} 下跌加仓%：跌幅超阈值 → 买入；</li>
 *   <li>{@code buyShare} 买入份额：每次加仓的固定份额；</li>
 *   <li>{@code sellShare} 卖出份额：每次减仓的固定份额；</li>
 *   <li>{@code initialShare} 初始仓位份额（V5.17）：仅<b>回测首日</b>一次性建仓——
 *       回测从"已持有 initialShare 份"起步而不是 0 仓；0＝不建仓（兼容旧配置）。
 *       实盘信号不受影响（始终按实际持仓判断）。</li>
 * </ul>
 *
 * <h3>锚点（对比区间的起点）</h3>
 * <p>"往前找 K线天数 那根 K 线，或上一次实际交易流水那根 K 线，<b>先找到哪根就用哪根</b>"
 * ——即取两者中<b>更靠近今天</b>的那根（等价于"最多往前 K线天数 根"的封顶）。
 * 每次实际交易后锚点前移到该笔交易，参考区间随之重置。
 *
 * <h3>触发与执行</h3>
 * <ul>
 *   <li><b>上涨减仓</b>：锚点 → 当日区间内<b>最低收盘价</b>为基准，涨幅 &gt; riseReducePct → 卖出
 *       {@code sellShare} 份（夹到不低于底仓）；</li>
 *   <li><b>下跌加仓</b>：锚点 → 当日区间内<b>最高收盘价</b>为基准，跌幅 &gt; fallAddPct → 买入
 *       {@code buyShare} 份（夹到不超过满仓）；</li>
 *   <li><b>冲突（V5.13 用户拍板：买入优先）</b>：同一根 bar 同时满足两者时先看买入；买入算不出可买份额
 *       （已满仓）时再看卖出——否则会出现"已满仓、双触发时永远不出信号"的死锁；</li>
 *   <li><b>无变化不出信号</b>：夹取后可交易份额为 0（已到底仓 / 已满仓）时不产生买卖信号。</li>
 * </ul>
 *
 * <h3>冷却与"信号不落地不算数"（V5.8/V5.10 口径保留）</h3>
 * <ul>
 *   <li><b>2 个交易日冷却</b>（V5.16 由 5 改 2）：最近一次<b>实际交易</b>后的 T+1~T+2 内不再提示买卖，只记 HOLD（不发邮件）；</li>
 *   <li><b>锚点＝实际交易</b>：信号发了但交易流水里没有对应买卖时该信号不算数（次日重新提示）；
 *       实盘锚点取交易流水（trade_flow），回测锚点取回测引擎的实际成交
 *       （运行中 scratch 维护，落库即 backtest_trade_detail）——回测里信号必然成交。</li>
 * </ul>
 */
@Component
public class OscillatingUpStrategy implements Strategy {

    /** 策略类型标识 */
    public static final String TYPE = "OSC_UP";

    /** 回测发起前资金校验的费用缓冲（买入约万 2.5 + 滑点余量，取 1%）：避免"满仓"因费用不足买不满而失真 */
    private static final BigDecimal FEE_BUFFER = new BigDecimal("1.01");

    /** 交易冷却期（交易日）：最近一次实际交易后的 T+1~T+5 内不重复提示买卖（T+6 起恢复） */
    private static final int COOLDOWN_TRADING_DAYS = 2;

    /** 建议说明最长保留长度（与 signal_record.suggest_desc 列宽一致） */
    private static final int DESC_MAX_LEN = 255;

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String name() {
        return "震荡向上";
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
        int window = Strategy.intOr(params, "windowDays", 0);
        if (window < 2 || window > 500) {
            throw new BizException("K线天数须在 2~500 之间");
        }
        checkPct(params, "riseReducePct", "上涨减仓");
        checkPct(params, "fallAddPct", "下跌加仓");
        if (Strategy.dec(params, "buyShare", BigDecimal.ZERO).compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("买入份额须大于 0");
        }
        if (Strategy.dec(params, "sellShare", BigDecimal.ZERO).compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("卖出份额须大于 0");
        }
        BigDecimal initial = Strategy.dec(params, "initialShare", BigDecimal.ZERO);
        if (initial.compareTo(BigDecimal.ZERO) < 0 || initial.compareTo(full) > 0) {
            throw new BizException("初始仓位份额须在 0 ~ 满仓份额之间，当前 " + initial);
        }
    }

    @Override
    public Signal generateSignal(StrategyContext context) {
        MarketDataSeries series = context.recentSeries();
        if (series.size() == 0) {
            return new Signal(Signal.HOLD, null, "无行情数据");
        }
        int index = series.size() - 1;
        BigDecimal price = series.get(index).close();
        Decision decision = judge(context.params(), series, index, nz(context.currentShares()),
                context.lastTradeDate());
        return new Signal(decision.direction(), price, decision.reason());
    }

    @Override
    public BacktestAction decide(int index, MarketDataSeries data, BacktestState state) {
        JsonNode params = (JsonNode) state.getScratch().get("params");
        // 回测锚点＝引擎实际成交（落库即 backtest_trade_detail），只用到成交所在 bar 的下标
        Integer lastIndex = (Integer) state.getScratch().get("lastIndex");
        BigDecimal price = data.get(index).close();
        // 初始仓位（V5.17）：回测首日一次性按 initialShare 建仓（仅一次），让回测从
        // "已持有仓位"起步而不是 0 仓；0＝不建仓。此后进入正常信号循环。
        if (Boolean.TRUE != state.getScratch().get("initialDone")) {
            state.getScratch().put("initialDone", Boolean.TRUE);
            BigDecimal initial = Strategy.dec(params, "initialShare", BigDecimal.ZERO);
            BigDecimal full = Strategy.dec(params, "fullShare", BigDecimal.ZERO);
            if (initial.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal initialTarget = initial.min(full);
                state.getScratch().put("lastDirection", Signal.BUY);
                state.getScratch().put("lastPrice", price);
                state.getScratch().put("lastIndex", index);
                return BacktestAction.buy(initialTarget,
                        "建立初始仓位 " + strip(initialTarget) + " 份");
            }
        }
        Decision decision = judge(params, data, index, state.getShares(),
                lastIndex == null ? null : data.get(lastIndex).date());
        if (Signal.HOLD.equals(decision.direction())) {
            return BacktestAction.hold();
        }
        BigDecimal delta = decision.targetShares().subtract(state.getShares()).abs()
                .setScale(2, RoundingMode.DOWN);
        if (delta.compareTo(BigDecimal.ZERO) <= 0) {
            return BacktestAction.hold();
        }
        // 冷却期（回测）：T+1~T+5 内不重复交易。必须在更新成交状态之前拦截——
        // 被冷却拦下的没有成交，不得重置锚点
        if (lastIndex != null && index - lastIndex <= COOLDOWN_TRADING_DAYS) {
            return BacktestAction.hold();
        }
        state.getScratch().put("lastDirection", decision.direction());
        state.getScratch().put("lastPrice", price);
        state.getScratch().put("lastIndex", index);
        return Signal.BUY.equals(decision.direction())
                ? BacktestAction.buy(delta, decision.reason())
                : BacktestAction.sell(delta, decision.reason());
    }

    @Override
    public void validateBacktest(MarketDataSeries series, int startIndex, JsonNode params,
                                 BigDecimal initialCapital) {
        if (startIndex < 0 || series.size() <= startIndex) {
            return;
        }
        BigDecimal full = Strategy.dec(params, "fullShare", BigDecimal.ZERO);
        BigDecimal price = series.get(startIndex).close();
        if (full.compareTo(BigDecimal.ZERO) <= 0 || price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal need = full.multiply(price).multiply(FEE_BUFFER);
        if (need.compareTo(initialCapital) > 0) {
            BigDecimal affordable = initialCapital.divide(price.multiply(FEE_BUFFER), 0, RoundingMode.DOWN);
            throw new BizException("初始本金不足：满仓 " + strip(full) + " 份 × 区间首日价 " + strip(price)
                    + " 元 ≈ " + need.setScale(0, RoundingMode.DOWN) + " 元（含 1% 费用缓冲），"
                    + "大于初始本金 " + strip(initialCapital) + " 元。"
                    + "请把初始本金调到 ≥ " + need.setScale(0, RoundingMode.UP) + " 元，"
                    + "或把满仓份额改到 " + affordable + " 份以内。");
        }
    }

    /**
     * 判定核心（实盘与回测共用）。
     * 两条路径的差异只在锚点来源：实盘＝交易流水最近一次买卖的日期，回测＝引擎成交明细的 bar。
     *
     * @param params        策略参数
     * @param series        行情序列（回测含预热段）
     * @param index         决策所在 bar 下标（实盘为最后一根）
     * @param currentShares 当前持仓份额
     * @param lastTradeDate 最近一次实际交易日期（null=还没有过买卖流水）
     * @return 决策（direction=HOLD 表示不出信号）
     */
    private Decision judge(JsonNode params, MarketDataSeries series, int index, BigDecimal currentShares,
                           LocalDate lastTradeDate) {
        BigDecimal full = Strategy.dec(params, "fullShare", BigDecimal.ZERO);
        BigDecimal base = Strategy.dec(params, "baseShare", BigDecimal.ZERO);
        if (full.compareTo(BigDecimal.ZERO) <= 0) {
            return Decision.hold("满仓份额未配置，无法判断");
        }
        BigDecimal current = nz(currentShares);
        int window = Math.max(Strategy.intOr(params, "windowDays", 60), 1);
        BigDecimal price = series.get(index).close();
        // 锚点：K线天数那根 与 上次实际交易那根 中更靠近今天的那根（先找到哪根用哪根）
        int windowStart = Math.max(0, index - window + 1);
        int tradeIdx = lastTradeDate == null ? -1 : barIndexOnOrAfter(series, lastTradeDate, index);
        int anchorIdx = Math.max(windowStart, tradeIdx);
        BigDecimal low = extreme(series, anchorIdx, index, true);
        BigDecimal high = extreme(series, anchorIdx, index, false);
        LocalDate anchorDate = series.get(anchorIdx).date();
        String windowText = "锚点 " + anchorDate + "（区间 " + strip(low) + " ~ " + strip(high) + "）";
        if (price == null || low == null || high == null) {
            return Decision.hold("行情数据不完整，无法判断（" + windowText + "）");
        }
        BigDecimal risePct = pctOf(low, price);
        BigDecimal fallPct = pctOf(price, high);
        BigDecimal riseLimit = Strategy.dec(params, "riseReducePct", BigDecimal.ZERO);
        BigDecimal fallLimit = Strategy.dec(params, "fallAddPct", BigDecimal.ZERO);
        boolean riseHit = risePct != null && risePct.compareTo(riseLimit) > 0;
        boolean fallHit = fallPct != null && fallPct.compareTo(fallLimit) > 0;

        // ① 下跌加仓（V5.13 用户拍板：买入优先）
        Decision buySide = null;
        if (fallHit) {
            BigDecimal buyShare = Strategy.dec(params, "buyShare", BigDecimal.ZERO);
            String why = "现价 " + strip(price) + " 较区间最高 " + strip(high) + " 下跌 "
                    + strip(fallPct) + "%（≥ 下跌加仓阈值 " + strip(fallLimit) + "%），" + windowText;
            buySide = buy(current, current.add(buyShare).min(full), base, full, why);
            if (!Signal.HOLD.equals(buySide.direction())) {
                return cooldownGate(series, index, lastTradeDate, buySide);
            }
        }
        // ② 上涨减仓
        Decision sellSide = null;
        if (riseHit) {
            BigDecimal sellShare = Strategy.dec(params, "sellShare", BigDecimal.ZERO);
            String why = "现价 " + strip(price) + " 较区间最低 " + strip(low) + " 上涨 "
                    + strip(risePct) + "%（≥ 上涨减仓阈值 " + strip(riseLimit) + "%），" + windowText;
            sellSide = sell(current, current.subtract(sellShare).max(base), base, full, why);
            if (!Signal.HOLD.equals(sellSide.direction())) {
                return cooldownGate(series, index, lastTradeDate, sellSide);
            }
        }
        if (buySide != null) {
            return cooldownGate(series, index, lastTradeDate, buySide);
        }
        if (sellSide != null) {
            return cooldownGate(series, index, lastTradeDate, sellSide);
        }
        return Decision.hold("未触发阈值（" + windowText + "）：距最低 +" + strip(risePct) + "% < 减仓阈值 "
                + strip(riseLimit) + "%，距最高 -" + strip(fallPct) + "% < 加仓阈值 " + strip(fallLimit) + "%");
    }

    /**
     * 买入决策：目标高于当前才出信号（夹取后可买份额为 0 = 已满仓，无变化不提信号）。
     * 目标先按「买入份额」计算，再受两条约束（用户 1.2 口径）：不超过满仓、<b>不低于底仓</b>
     * ——因此从 0 仓起步时第一次买入会直接建到底仓（买入份额小于底仓时以底仓为准），文案里会点明。
     */
    private Decision buy(BigDecimal current, BigDecimal target, BigDecimal base, BigDecimal full, String why) {
        BigDecimal aim = target.max(base).min(full);
        BigDecimal delta = aim.subtract(current);
        if (delta.compareTo(BigDecimal.ZERO) <= 0) {
            return Decision.hold(why + "，但当前 " + strip(current) + " 份已达满仓上限 " + strip(full)
                    + " 份，无变化不提信号");
        }
        String lift = target.compareTo(base) < 0 && aim.compareTo(target) > 0
                ? "（加仓后不足底仓，按底仓 " + strip(base) + " 份建仓）" : "";
        return new Decision(Signal.BUY, aim,
                trim(why + "，建议买入 " + strip(delta) + " 份至 " + strip(aim) + " 份" + lift));
    }

    /** 卖出决策：目标低于当前才出信号（夹取后可卖份额为 0 = 已到底仓，无变化不提信号） */
    private Decision sell(BigDecimal current, BigDecimal target, BigDecimal base, BigDecimal full, String why) {
        BigDecimal aim = target.max(base).min(full);
        BigDecimal delta = current.subtract(aim);
        if (delta.compareTo(BigDecimal.ZERO) <= 0) {
            return Decision.hold(why + "，但当前 " + strip(current) + " 份已到底仓下限 " + strip(base)
                    + " 份，无变化不提信号");
        }
        return new Decision(Signal.SELL, aim, trim(why + "，建议卖出 " + strip(delta) + " 份至 " + strip(aim) + " 份"));
    }

    /**
     * 5 个交易日冷却闸：规则触发了买卖建议，但距最近一次<b>实际交易</b>不足 COOLDOWN_TRADING_DAYS 个交易日时
     * 改记 HOLD（说明保留触发依据与冷却原因；用户口径：信号可发、买卖建议不给）。
     */
    private Decision cooldownGate(MarketDataSeries series, int index, LocalDate lastTradeDate, Decision decision) {
        if (Signal.HOLD.equals(decision.direction()) || lastTradeDate == null) {
            return decision;
        }
        int gap = barsSince(series, lastTradeDate, index);
        if (gap > COOLDOWN_TRADING_DAYS) {
            return decision;
        }
        return Decision.hold(trim(decision.reason() + "。距上次实际交易 " + lastTradeDate + " 仅 " + gap
                + " 个交易日（2 个交易日内不重复交易），今日不提供买卖建议"));
    }

    /** 区间极值：bar 下标 [from, index]（含两端）内的最低 / 最高收盘价 */
    private BigDecimal extreme(MarketDataSeries series, int from, int index, boolean low) {
        BigDecimal result = null;
        for (int i = Math.max(0, from); i <= index; i++) {
            BigDecimal close = series.get(i).close();
            if (close == null) {
                continue;
            }
            if (result == null) {
                result = close;
            } else if (low ? close.compareTo(result) < 0 : close.compareTo(result) > 0) {
                result = close;
            }
        }
        return result;
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

    /** 距某日的交易日 bar 数（最小 1）；该日早于加载窗口时返回 Integer.MAX_VALUE（视为无冷却） */
    private int barsSince(MarketDataSeries series, LocalDate date, int index) {
        int at = barIndexOnOrAfter(series, date, index);
        return at < 0 ? Integer.MAX_VALUE : index - at;
    }

    /** 相对涨跌幅（%），保留 2 位；基准非法返回 null */
    private BigDecimal pctOf(BigDecimal from, BigDecimal to) {
        if (from == null || to == null || from.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return to.subtract(from).multiply(BigDecimal.valueOf(100)).divide(from, 2, RoundingMode.HALF_UP);
    }

    /** 阈值校验：必须在 (0, 100) 之间 */
    private void checkPct(JsonNode params, String field, String label) {
        BigDecimal pct = Strategy.dec(params, field, BigDecimal.ZERO);
        if (pct.compareTo(BigDecimal.ZERO) <= 0 || pct.compareTo(BigDecimal.valueOf(100)) >= 0) {
            throw new BizException(label + "阈值须在 0~100 之间（不含端点），当前 " + pct);
        }
    }

    /** 去掉无意义的小数零，便于文案阅读 */
    private String strip(BigDecimal value) {
        return value == null ? "无" : value.stripTrailingZeros().toPlainString();
    }

    /** 截断到建议说明列宽 */
    private String trim(String text) {
        return text.length() <= DESC_MAX_LEN ? text : text.substring(0, DESC_MAX_LEN);
    }

    /** null 归一为零 */
    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * 一次判定的结果。
     *
     * @param direction    BUY / SELL / HOLD（HOLD = 不出信号）
     * @param targetShares 目标持仓份额（HOLD 时无意义）
     * @param reason       建议说明（含触发依据、锚点区间与目标份额）
     */
    private record Decision(String direction, BigDecimal targetShares, String reason) {

        static Decision hold(String reason) {
            return new Decision(Signal.HOLD, BigDecimal.ZERO, reason);
        }
    }
}
