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
 * 三均线右侧策略（TRIPLE_MA，V6.32 用户新增；V6.33 调整「中&gt;快&gt;慢」的档位；V6.34 更名，原"三均线"）：
 * 快线/中线/慢线三条均线的**排列关系**决定目标仓位（用户口径的六种排列一一对应）：
 * <ul>
 *   <li>快线 &gt; 中线 &gt; 慢线 → **全仓**（唯一的最强多头排列：快线领涨）；</li>
 *   <li>中线 &gt; 快线 &gt; 慢线 → **中仓**（V6.33 由"全仓"调整为"中仓"）；</li>
 *   <li>中线 &gt; 慢线 &gt; 快线 → **中仓**；</li>
 *   <li>快线 &gt; 慢线 &gt; 中线 → **底仓**；</li>
 *   <li>慢线 &gt; 快线 &gt; 中线 → **底仓**；</li>
 *   <li>慢线 &gt; 中线 &gt; 快线 → **底仓**；</li>
 *   <li>任两线相等 → 状态不明确，维持现仓位。</li>
 * </ul>
 *
 * <h3>参数（7 个）</h3>
 * <ul>
 *   <li>{@code fullShare} 满仓份额 / {@code midShare} 中仓份额 / {@code baseShare} 底仓份额
 *       （须 底仓 &lt; 中仓 &lt; 满仓）；</li>
 *   <li>{@code fastMaDays} 快线（默认 20）/ {@code midMaDays} 中线（默认 60）/ {@code slowMaDays}
 *       慢线（默认 180）：收盘价的简单移动平均（含当日），取值 2~500，**须 快线 &lt; 中线 &lt; 慢线**；</li>
 *   <li>{@code cooldownDays} 冷静天数：最近一次**实际交易**后的 N 个交易日内不调仓（0=不冷静，0~500）。</li>
 * </ul>
 *
 * <h3>执行口径（目标仓位模型，与均线突破 MA_BREAK 同一套引擎语义）</h3>
 * <ul>
 *   <li>每天**收盘后**按三条均线的排列算目标仓位，实际 ≠ 目标 → **次日开盘价一次性调仓**；已在目标 → 不动；</li>
 *   <li>状态制（不是"穿越动作"制）：信号不会丢，调仓频率完全由冷静期控制；</li>
 *   <li>**没有「初始仓位份额」参数**（用户口径）：回测首日即按当日排列对齐到目标仓位；</li>
 *   <li>均线样本不足（预热不够）时不判定，维持现仓位。</li>
 * </ul>
 */
@Component
public class TripleMaStrategy implements Strategy {

    /** 策略类型标识 */
    public static final String TYPE = "TRIPLE_MA";

    /** 回测发起前资金校验的费用缓冲（买入约万 2.5 + 滑点余量，取 1%） */
    private static final BigDecimal FEE_BUFFER = new BigDecimal("1.01");

    /** 费率配置（ETF 最低佣金用于判断"补买是否注定无法成交"） */
    private final FeeProperties feeProperties;

    public TripleMaStrategy(FeeProperties feeProperties) {
        this.feeProperties = feeProperties;
    }

    /** 参数名：快线均线（日） */
    private static final String P_FAST_MA = "fastMaDays";

    /** 参数名：中线均线（日） */
    private static final String P_MID_MA = "midMaDays";

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
        return "三均线右侧";
    }

    @Override
    public void validateParams(JsonNode params) {
        BigDecimal full = Strategy.dec(params, "fullShare", BigDecimal.ZERO);
        BigDecimal mid = Strategy.dec(params, "midShare", BigDecimal.ZERO);
        BigDecimal base = Strategy.dec(params, "baseShare", BigDecimal.ZERO);
        if (full.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("满仓份额须大于 0");
        }
        if (base.compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException("底仓份额不能为负");
        }
        if (base.compareTo(mid) >= 0 || mid.compareTo(full) >= 0) {
            throw new BizException("三档份额须满足 底仓 < 中仓 < 满仓（当前 "
                    + base.stripTrailingZeros().toPlainString() + " / " + mid.stripTrailingZeros().toPlainString()
                    + " / " + full.stripTrailingZeros().toPlainString() + "）");
        }
        checkMaDays(params, P_FAST_MA, "快线");
        checkMaDays(params, P_MID_MA, "中线");
        checkMaDays(params, P_SLOW_MA, "慢线");
        int fastDays = Strategy.intOr(params, P_FAST_MA, 20);
        int midDays = Strategy.intOr(params, P_MID_MA, 60);
        int slowDays = Strategy.intOr(params, P_SLOW_MA, 180);
        if (!(fastDays < midDays && midDays < slowDays)) {
            throw new BizException("均线周期须满足 快线 < 中线 < 慢线（当前 "
                    + fastDays + " / " + midDays + " / " + slowDays + "）");
        }
        int cooldown = Strategy.intOr(params, P_COOLDOWN_DAYS, 0);
        if (cooldown < 0 || cooldown > 500) {
            throw new BizException("冷静天数须在 0~500 之间（0=不冷静），当前 " + cooldown);
        }
    }

    /**
     * 按三条均线的排列算目标仓位（用户口径的六种排列；任两线相等返回 null = 状态不明确、维持现仓位）。
     *
     * @return 目标份额；null = 不调仓
     */
    private BigDecimal targetOf(BigDecimal fast, BigDecimal mid, BigDecimal slow, JsonNode params) {
        if (fast.compareTo(mid) == 0 || mid.compareTo(slow) == 0 || fast.compareTo(slow) == 0) {
            return null;
        }
        BigDecimal full = Strategy.dec(params, "fullShare", BigDecimal.ZERO);
        BigDecimal midShare = Strategy.dec(params, "midShare", BigDecimal.ZERO);
        BigDecimal base = Strategy.dec(params, "baseShare", BigDecimal.ZERO);
        // 最高线是中 → 中仓（中>快>慢 或 中>慢>快；V6.33 用户口径：中>快>慢 由"全仓"改为"中仓"）
        if (mid.compareTo(fast) > 0 && mid.compareTo(slow) > 0) {
            return midShare;
        }
        // 最高线是快、且 中>慢（快>中>慢：唯一的最强多头排列）→ 全仓
        if (fast.compareTo(mid) > 0 && mid.compareTo(slow) > 0) {
            return full;
        }
        // 其余三种（快>慢>中 / 慢>快>中 / 慢>中>快）→ 底仓
        return base;
    }

    /** 排列的中文描述（用于信号/回测文案）：按三线从高到低输出「快>中>慢」这类排列（调用处已保证三值互不相等） */
    private String orderDesc(BigDecimal fast, BigDecimal mid, BigDecimal slow) {
        String[] names = {"快", "中", "慢"};
        BigDecimal[] values = {fast, mid, slow};
        Integer[] order = {0, 1, 2};
        java.util.Arrays.sort(order, (a, b) -> values[b].compareTo(values[a]));
        return names[order[0]] + ">" + names[order[1]] + ">" + names[order[2]];
    }

    @Override
    public Signal generateSignal(StrategyContext context) {
        MarketDataSeries series = context.recentSeries();
        if (series.size() < 3) {
            return new Signal(Signal.HOLD, null, "行情数据不足，暂无法判断");
        }
        JsonNode params = context.params();
        int fastDays = Math.max(Strategy.intOr(params, P_FAST_MA, 20), 2);
        int midDays = Math.max(Strategy.intOr(params, P_MID_MA, 60), 2);
        int slowDays = Math.max(Strategy.intOr(params, P_SLOW_MA, 180), 2);
        int cooldown = Math.max(Strategy.intOr(params, P_COOLDOWN_DAYS, 0), 0);
        int index = series.size() - 1;
        BigDecimal price = series.get(index).close();
        LocalDate lastTradeDate = context.lastTradeDate();
        int barsSinceTrade = lastTradeDate == null ? Integer.MAX_VALUE : barsSince(series, lastTradeDate, index);
        BigDecimal fast = maAt(series, index, fastDays);
        BigDecimal mid = maAt(series, index, midDays);
        BigDecimal slow = maAt(series, index, slowDays);
        if (fast == null || mid == null || slow == null) {
            return new Signal(Signal.HOLD, price, "历史数据不足，均线信号暂无法判定");
        }
        // 冷却中：不给操作建议（状态每日重评，冷却期满当天按最新状态给出）
        if (cooldown > 0 && barsSinceTrade < cooldown) {
            return new Signal(Signal.HOLD, price, "冷静期内（距上次实际交易 " + barsSinceTrade
                    + " 个交易日，冷静 " + cooldown + " 天），暂不给操作建议");
        }
        BigDecimal target = targetOf(fast, mid, slow, params);
        if (target == null) {
            return new Signal(Signal.HOLD, price, "任两线相等（状态不明确），维持现仓位");
        }
        String stateDesc = "排列 " + orderDesc(fast, mid, slow) + "（快 " + fastDays + " 日 " + strip(fast)
                + "，中 " + midDays + " 日 " + strip(mid) + "，慢 " + slowDays + " 日 " + strip(slow) + "）";
        BigDecimal current = context.currentShares() == null ? BigDecimal.ZERO : context.currentShares();
        if (target.compareTo(current) == 0) {
            return new Signal(Signal.HOLD, price, stateDesc + "，已处于目标仓位（" + strip(target) + " 份），无操作建议");
        }
        if (target.compareTo(current) > 0) {
            return new Signal(Signal.BUY, price, stateDesc + " → 目标 " + strip(target) + " 份，建议买入");
        }
        return new Signal(Signal.SELL, price, stateDesc + " → 目标 " + strip(target) + " 份，建议卖出");
    }

    @Override
    public BacktestAction decide(int index, MarketDataSeries data, BacktestState state) {
        JsonNode params = (JsonNode) state.getScratch().get("params");
        int fastDays = Math.max(Strategy.intOr(params, P_FAST_MA, 20), 2);
        int midDays = Math.max(Strategy.intOr(params, P_MID_MA, 60), 2);
        int slowDays = Math.max(Strategy.intOr(params, P_SLOW_MA, 180), 2);
        int cooldown = Math.max(Strategy.intOr(params, P_COOLDOWN_DAYS, 0), 0);
        Integer lastIndex = (Integer) state.getScratch().get("lastIndex");
        BigDecimal currClose = data.get(index).close();
        if (currClose == null) {
            return BacktestAction.hold();
        }
        BigDecimal fast = maAt(data, index, fastDays);
        BigDecimal mid = maAt(data, index, midDays);
        BigDecimal slow = maAt(data, index, slowDays);
        if (fast == null || mid == null || slow == null) {
            return BacktestAction.hold();
        }
        BigDecimal current = state.getShares();
        // 冷却期内不调仓；状态每日重评，冷却期满当天拿到的就是最新目标
        if (cooldown > 0 && lastIndex != null && index - lastIndex < cooldown) {
            return new BacktestAction(BacktestAction.HOLD, BigDecimal.ZERO, "冷静期内（剩 "
                    + (cooldown - (index - lastIndex)) + " 个交易日），暂不调仓");
        }
        BigDecimal target = targetOf(fast, mid, slow, params);
        if (target == null || target.compareTo(current) == 0) {
            return BacktestAction.hold();
        }
        String stateDesc = "排列 " + orderDesc(fast, mid, slow);
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
                    stateDesc + "，状态满足买入 → 买入至 " + strip(target) + " 份");
        }
        state.getScratch().put("lastIndex", index);
        return BacktestAction.sell(current.subtract(target).setScale(2, RoundingMode.DOWN),
                stateDesc + "，状态满足卖出 → 卖出至 " + strip(target) + " 份");
    }

    @Override
    public void validateBacktest(MarketDataSeries series, int startIndex, JsonNode params,
                                 BigDecimal initialCapital) {
        if (startIndex < 0 || series.size() <= startIndex) {
            return;
        }
        int fastDays = Math.max(Strategy.intOr(params, P_FAST_MA, 20), 2);
        int midDays = Math.max(Strategy.intOr(params, P_MID_MA, 60), 2);
        int slowDays = Math.max(Strategy.intOr(params, P_SLOW_MA, 180), 2);
        int need = Math.max(fastDays, Math.max(midDays, slowDays));
        // 均线周期大于预热历史的硬校验：区间前段均线值不足会让信号推迟
        if (startIndex + 1 < need) {
            throw new BizException("均线周期 " + need + " 个交易日超过了回测区间首日前的预热数据（"
                    + (startIndex + 1) + " 根）：区间前段的均线值不足，三均线信号会推迟。"
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
