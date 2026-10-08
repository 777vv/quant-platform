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
 * 均线止盈/加仓策略（MA_TP_GRID，V5.88 用户口径）。围绕**一条基准均线**画上下两条百分比带：
 *
 * <h3>参数（8 个）</h3>
 * <ul>
 *   <li>{@code initialShare} 初始仓位份额：仅回测首日一次性建仓（与震荡向上/均线突破同语义），0 = 不建仓；</li>
 *   <li>{@code baseShare} 底仓份额：上沿触发后的**卖出目标**（卖出至底仓）；</li>
 *   <li>{@code fullShare} 满仓份额：下沿触发后的**买入目标**（买入至满仓）；</li>
 *   <li>{@code baselineMaDays} 基准均线（日）：以这条均线的当日值作为带的中心（如 120 = 120 日均线）；</li>
 *   <li>{@code reboundMaDays} 回踩均线（日，V5.92 独立配置）：上下沿之间时，收盘回落到这条均线的当日值
 *       （≤ 该均线）→ 调回初始仓位份额（"现价回踩回踩均线时保持初始份额"）；</li>
 *   <li>{@code upperPct} 上限百分比：收盘价 ≥ 基准均线 × (1 + 上限%) → **卖出至底仓**；</li>
 *   <li>{@code lowerPct} 下限百分比：收盘价 ≤ 基准均线 × (1 − 下限%) → **买入至满仓**；</li>
 *   <li>{@code cooldownDays} 冷静天数：最近一次实际交易后的 N 个交易日内不调仓（0=关闭），
 *       状态每日重评，冷却期满当天按最新状态执行（与均线突破 V5.87 同一模型）。</li>
 * </ul>
 *
 * <h3>触发判定（全状态驱动，与均线突破 V5.87 同一模型）</h3>
 * <ul>
 *   <li>每天收盘按<b>状态</b>（而非穿越动作）计算目标仓位：
 *       收盘 ≥ 上沿（基准均线×(1+上限%)）→ 目标 = 底仓；收盘 ≤ 下沿（基准均线×(1−下限%)）→ 目标 = 满仓；</li>
 *   <li><b>回踩均线</b>（V5.92，替代原"回踩基准线"）：上下沿之间时，收盘回落到「回踩均线」的当日值
 *       （≤ 该均线值）→ 目标 = **初始仓位份额**；回踩均线可与基准均线相同（行为同 V5.91），
 *       也可配更短周期（如基准 120 / 回踩 60 = 回踩到 60 日线就补回）；收盘仍在回踩均线上方 → 维持现仓位；</li>
 *   <li>实际仓位 = 目标仓位 → 不动；实际 ≠ 目标 → 次日开盘价（ETF）/净值（场外）一次性调仓到目标；</li>
 *   <li>均线样本不足时不判定，维持现仓位（预热取基准/回踩两条均线中较长者）。</li>
 * </ul>
 *
 * <p>与均线突破（MA_BREAK）的差异：MA_BREAK 的中性区=维持现仓位、上下沿触发的是"突破/跌破操作"；
 * 本策略上沿恒为卖出至底仓、下沿恒为买入至满仓、**回踩均线调回初始份额**——围绕均线的
 * "高抛（上沿）—回踩补回初始—低吸（下沿满仓）"三段形态。
 */
@Component
public class MaTakeProfitGridStrategy implements Strategy {

    /** 策略类型标识 */
    public static final String TYPE = "MA_TP_GRID";

    /** 回测发起前资金校验的费用缓冲（买入约万 2.5 + 滑点余量，取 1%） */
    private static final BigDecimal FEE_BUFFER = new BigDecimal("1.01");

    /** 费率配置（ETF 最低佣金用于判断"补买是否注定无法成交"） */
    private final FeeProperties feeProperties;

    public MaTakeProfitGridStrategy(FeeProperties feeProperties) {
        this.feeProperties = feeProperties;
    }

    /** 参数名：基准均线（日） */
    private static final String P_BASELINE_MA = "baselineMaDays";

    /** 参数名：回踩均线（日，独立于基准均线；上下沿之间收盘回落到它 → 调回初始份额，V5.92） */
    private static final String P_REBOUND_MA = "reboundMaDays";

    /** 参数名：上限百分比（高于基准均线的百分比，触发卖出至底仓） */
    private static final String P_UPPER_PCT = "upperPct";

    /** 参数名：下限百分比（低于基准均线的百分比，触发买入至满仓） */
    private static final String P_LOWER_PCT = "lowerPct";

    /** 参数名：冷静天数（最近一次交易后 N 个交易日内不调仓，0=关闭） */
    private static final String P_COOLDOWN_DAYS = "cooldownDays";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String name() {
        return "均线止盈/加仓";
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
        checkMaDays(params, P_BASELINE_MA);
        checkMaDays(params, P_REBOUND_MA);
        BigDecimal upper = Strategy.dec(params, P_UPPER_PCT, BigDecimal.ZERO);
        BigDecimal lower = Strategy.dec(params, P_LOWER_PCT, BigDecimal.ZERO);
        if (upper.compareTo(BigDecimal.ZERO) <= 0 || upper.compareTo(new BigDecimal("100")) > 0) {
            throw new BizException("上限百分比须在 0~100 之间（不含 0），当前 " + upper);
        }
        if (lower.compareTo(BigDecimal.ZERO) <= 0 || lower.compareTo(new BigDecimal("100")) > 0) {
            throw new BizException("下限百分比须在 0~100 之间（不含 0），当前 " + lower);
        }
        int cooldown = Strategy.intOr(params, P_COOLDOWN_DAYS, 0);
        if (cooldown < 0 || cooldown > 500) {
            throw new BizException("冷静天数须在 0~500 之间（0=不冷静），当前 " + cooldown);
        }
    }

    @Override
    public Signal generateSignal(StrategyContext context) {
        MarketDataSeries series = context.recentSeries();
        // 需要当日收盘 + 均线窗口历史：样本不足先 HOLD（数据每天积累，缺了自然出信号）
        if (series.size() < 3) {
            return new Signal(Signal.HOLD, null, "行情数据不足，暂时无法判断");
        }
        JsonNode params = context.params();
        int baseline = Math.max(Strategy.intOr(params, P_BASELINE_MA, 120), 2);
        BigDecimal upperPct = Strategy.dec(params, P_UPPER_PCT, BigDecimal.TEN);
        BigDecimal lowerPct = Strategy.dec(params, P_LOWER_PCT, BigDecimal.TEN);
        int cooldown = Math.max(Strategy.intOr(params, P_COOLDOWN_DAYS, 0), 0);
        int index = series.size() - 1;
        BigDecimal price = series.get(index).close();
        if (price == null) {
            return new Signal(Signal.HOLD, null, "当日收盘价缺失，暂时无法判断");
        }
        BigDecimal ma = maAt(series, index, baseline);
        int rebound = Math.max(Strategy.intOr(params, P_REBOUND_MA, baseline), 2);
        BigDecimal reboundMa = maAt(series, index, rebound);
        if (ma == null || reboundMa == null) {
            return new Signal(Signal.HOLD, price, "历史数据不足，" + baseline + "/" + rebound
                    + " 日均线暂无法计算，保持现仓位");
        }
        BigDecimal current = context.currentShares() == null ? BigDecimal.ZERO : context.currentShares();
        BigDecimal initial = Strategy.dec(params, "initialShare", BigDecimal.ZERO);
        BigDecimal base = Strategy.dec(params, "baseShare", BigDecimal.ZERO);
        BigDecimal full = Strategy.dec(params, "fullShare", BigDecimal.ZERO);
        BigDecimal upperLine = ma.multiply(BigDecimal.ONE.add(upperPct.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)))
                .setScale(4, RoundingMode.HALF_UP);
        BigDecimal lowerLine = ma.multiply(BigDecimal.ONE.subtract(lowerPct.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)))
                .setScale(4, RoundingMode.HALF_UP);
        // 冷却期：不给操作建议（状态每日重评，冷却期满当天按最新状态给出）
        LocalDate lastTradeDate = context.lastTradeDate();
        int barsSinceTrade = lastTradeDate == null ? Integer.MAX_VALUE : barsSince(series, lastTradeDate, index);
        if (cooldown > 0 && barsSinceTrade < cooldown) {
            return new Signal(Signal.HOLD, price, "冷却期内（距上次实际交易 " + barsSinceTrade
                    + " 个交易日，冷却 " + cooldown + " 天），暂不给操作建议");
        }
        Band band = bandOf(price, ma, reboundMa, upperLine, lowerLine);
        String bandDesc = "收盘 " + strip(price) + "、" + baseline + " 日均线 " + strip(ma)
                + "，上沿 " + strip(upperLine) + "（+" + strip(upperPct) + "%）/ 下沿 " + strip(lowerLine)
                + "（−" + strip(lowerPct) + "%）/ 回踩线（" + rebound + " 日）" + strip(reboundMa);
        // NEUTRAL（回踩均线上方、上沿之下）不设目标仓位：维持现仓位（V5.91 用户口径）
        if (band == Band.NEUTRAL) {
            return new Signal(Signal.HOLD, price, bandDesc + "；价格处于均线上方、上沿之下，维持现仓位（"
                    + strip(current) + " 份），无操作建议");
        }
        BigDecimal target = band.targetOf(initial, base, full);
        String bandText = switch (band) {
            case UPPER -> "突破上沿，满足卖出";
            case LOWER -> "跌破下沿，满足买入";
            case REBOUND -> "回踩到基准均线，应回到初始份额";
            default -> "";
        };
        if (target.compareTo(current) > 0) {
            return new Signal(Signal.BUY, price, bandDesc + "；" + bandText + "，建议买入至满仓 " + strip(full) + " 份");
        }
        if (target.compareTo(current) < 0) {
            return new Signal(Signal.SELL, price, bandDesc + "；" + bandText + "，建议卖出至底仓 " + strip(base) + " 份");
        }
        return new Signal(Signal.HOLD, price, bandDesc + "；" + bandText + "，当前仓位已符合目标（"
                + strip(current) + " 份），无操作建议");
    }

    @Override
    public BacktestAction decide(int index, MarketDataSeries data, BacktestState state) {
        JsonNode params = (JsonNode) state.getScratch().get("params");
        // 初始仓位：回测首日一次性按 initialShare 建仓（与震荡向上/均线突破同语义），仅一次
        if (Boolean.TRUE != state.getScratch().get("initialDone")) {
            state.getScratch().put("initialDone", Boolean.TRUE);
            BigDecimal initial = Strategy.dec(params, "initialShare", BigDecimal.ZERO);
            BigDecimal full = Strategy.dec(params, "fullShare", BigDecimal.ZERO);
            if (initial.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal price = data.get(index).close();
                state.getScratch().put("lastIndex", index);
                return BacktestAction.buy(initial.min(full), "建立初始仓位 " + strip(initial.min(full)) + " 份");
            }
        }
        if (index < 1) {
            return BacktestAction.hold();
        }
        int baseline = Math.max(Strategy.intOr(params, P_BASELINE_MA, 120), 2);
        BigDecimal upperPct = Strategy.dec(params, P_UPPER_PCT, BigDecimal.TEN);
        BigDecimal lowerPct = Strategy.dec(params, P_LOWER_PCT, BigDecimal.TEN);
        int cooldown = Math.max(Strategy.intOr(params, P_COOLDOWN_DAYS, 0), 0);
        Integer lastIndex = (Integer) state.getScratch().get("lastIndex");
        BigDecimal currClose = data.get(index).close();
        if (currClose == null) {
            return BacktestAction.hold();
        }
        BigDecimal ma = maAt(data, index, baseline);
        int rebound = Math.max(Strategy.intOr(params, P_REBOUND_MA, baseline), 2);
        BigDecimal reboundMa = maAt(data, index, rebound);
        if (ma == null || reboundMa == null) {
            // 均线样本不足：不判定（与均线突破同口径）
            return BacktestAction.hold();
        }
        BigDecimal upperLine = ma.multiply(BigDecimal.ONE.add(upperPct.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)));
        BigDecimal lowerLine = ma.multiply(BigDecimal.ONE.subtract(lowerPct.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)));
        // 冷却期内不调仓：状态每日重评，冷却期满当天拿到的是最新目标，无需暂挂/复核机制
        if (cooldown > 0 && lastIndex != null && index - lastIndex < cooldown) {
            return new BacktestAction(BacktestAction.HOLD, BigDecimal.ZERO, "冷却期内（剩 "
                    + (cooldown - (index - lastIndex)) + " 个交易日），暂不调仓");
        }
        Band band = bandOf(currClose, ma, reboundMa, upperLine, lowerLine);
        // NEUTRAL（回踩均线上方、上沿之下）：维持现仓位，不设目标（V5.91 用户口径）
        if (band == Band.NEUTRAL) {
            return BacktestAction.hold();
        }
        BigDecimal initial = Strategy.dec(params, "initialShare", BigDecimal.ZERO);
        BigDecimal base = Strategy.dec(params, "baseShare", BigDecimal.ZERO);
        BigDecimal full = Strategy.dec(params, "fullShare", BigDecimal.ZERO);
        BigDecimal target = band.targetOf(initial, base, full);
        BigDecimal current = state.getShares();
        if (target.compareTo(current) == 0) {
            return BacktestAction.hold();
        }
        String triggerDesc = "收盘 " + strip(currClose) + "、" + baseline + " 日均线 " + strip(ma)
                + "（上沿 " + strip(upperLine) + " / 下沿 " + strip(lowerLine) + " / 回踩线 " + strip(reboundMa) + "），" + band.text();
        if (target.compareTo(current) > 0) {
            // 现金连最低佣金都覆盖不了时这笔补买注定无法成交——不发单、不重置冷却（V5.86 同款教训）
            BigDecimal minFee = data.isEtf()
                    ? BigDecimal.valueOf(feeProperties.getEtfMinCommission())
                    : BigDecimal.ZERO;
            if (state.getCash().compareTo(minFee) <= 0) {
                return BacktestAction.hold();
            }
            state.getScratch().put("lastIndex", index);
            return BacktestAction.buy(target.subtract(current).setScale(2, RoundingMode.DOWN),
                    triggerDesc + " → 买入至满仓 " + strip(full) + " 份");
        }
        state.getScratch().put("lastIndex", index);
        return BacktestAction.sell(current.subtract(target).setScale(2, RoundingMode.DOWN),
                triggerDesc + " → 卖出至目标 " + strip(target) + " 份");
    }

    @Override
    public void validateBacktest(MarketDataSeries series, int startIndex, JsonNode params,
            BigDecimal initialCapital) {
        if (startIndex < 0 || series.size() <= startIndex) {
            return;
        }
        int baseline = Math.max(Strategy.intOr(params, P_BASELINE_MA, 120), 2);
        // 均线周期大于预热历史的硬校验：区间前段均线值不足会让上下沿判定推迟（与均线突破同口径）
        if (startIndex + 1 < baseline) {
            throw new BizException("基准均线周期 " + baseline + " 个交易日超过了回测区间首日前的预热数据（"
                    + (startIndex + 1) + " 根）：区间前段的均线值不足，上下沿信号会推迟。"
                    + "请把开始日期再往前调，或把基准均线周期调小到 " + (startIndex + 1) + " 以内。");
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
                    + "或把满仓份额改成 " + affordable + " 份以内。");
        }
    }

    /**
     * 价格所处的带（V5.91 用户口径：上下沿之间的目标改为「回踩基准线时才回初始份额」）：
     * 上沿之上=UPPER（目标底仓）、下沿之下=LOWER（目标满仓）、
     * 之间时再分——回踩到基准均线（收盘 ≤ 当日均线值）=REBOUND（目标初始份额），仍在均线上方=NEUTRAL（维持现仓位）。
     */
    private Band bandOf(BigDecimal price, BigDecimal ma, BigDecimal reboundMa, BigDecimal upperLine,
            BigDecimal lowerLine) {
        if (price.compareTo(upperLine) >= 0) {
            return Band.UPPER;
        }
        if (price.compareTo(lowerLine) <= 0) {
            return Band.LOWER;
        }
        // 上下沿之间：收盘回落到「回踩均线」当日值（含跌破它）→ 回踩补回初始份额；
        // 仍在回踩均线上方 → 维持现仓位
        return price.compareTo(reboundMa) <= 0 ? Band.REBOUND : Band.NEUTRAL;
    }

    /** 价格带枚举：每档带自己的目标仓位与描述文案 */
    private enum Band {
        UPPER,
        LOWER,
        /** 回踩基准均线（上下沿之间）：目标初始份额（用户口径："现价回踩基准线时保持初始份额"） */
        REBOUND,
        /** 上下沿之间但仍在基准均线上方：不触发任何调仓 */
        NEUTRAL;

        /** 该带的目标仓位：UPPER=底仓、LOWER=满仓、REBOUND=初始份额、NEUTRAL=null（维持现仓位不调） */
        BigDecimal targetOf(BigDecimal initial, BigDecimal base, BigDecimal full) {
            return switch (this) {
                case UPPER -> base;
                case LOWER -> full;
                case REBOUND -> initial;
                case NEUTRAL -> null;
            };
        }

        String text() {
            return switch (this) {
                case UPPER -> "价格处于上沿之上（止盈区）";
                case LOWER -> "价格处于下沿之下（加仓区）";
                case REBOUND -> "价格回踩到基准均线（回踩区）";
                case NEUTRAL -> "价格处于均线上方、上沿之下";
            };
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
    private int barIndexOnOrAfter(MarketDataSeries series, java.time.LocalDate date, int index) {
        for (int i = 0; i <= index; i++) {
            if (!series.get(i).date().isBefore(date)) {
                return i;
            }
        }
        return -1;
    }

    /** 距某日的交易日 bar 数；该日早于加载窗口时返回 Integer.MAX_VALUE（视为无冷却） */
    private int barsSince(MarketDataSeries series, java.time.LocalDate date, int index) {
        int at = barIndexOnOrAfter(series, date, index);
        return at < 0 ? Integer.MAX_VALUE : index - at;
    }

    /** 基准均线周期校验：2~500 个交易日 */
    private void checkMaDays(JsonNode params, String field) {
        int days = Strategy.intOr(params, field, 0);
        if (days < 2 || days > 500) {
            throw new BizException("基准均线周期须在 2~500 之间（交易日），当前 " + days);
        }
    }

    /** 去掉无意义的小数零，便于文案阅读 */
    private String strip(BigDecimal value) {
        return value == null ? "—" : value.stripTrailingZeros().toPlainString();
    }
}
