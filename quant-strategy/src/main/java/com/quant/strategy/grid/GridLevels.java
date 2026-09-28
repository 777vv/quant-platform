package com.quant.strategy.grid;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.quant.common.exception.BizException;

/**
 * 网格格线（V5.27，红利网格/纳指网格共用）：把 [lower, upper] 按等差或等比切成 grids 段，
 * 并负责"价格落在第几格"与"跨了几格"的换算。
 *
 * <h3>格位（position）语义</h3>
 * <ul>
 *   <li>{@code 0 .. grids-1}：价格落在区间内的某个格子；</li>
 *   <li>{@code -1}：价格跌破下沿（区间外下方，视为区间下方的"第 -1 格"）；</li>
 *   <li>{@code grids}：价格涨到上沿或以上（区间外上方）。</li>
 * </ul>
 * 跨格数 = 两个格位之差：从格位 5 落到格位 2 = 下跌 3 格（买 3 格），反之亦然。
 * 这个口径让"一天跳空穿多格"能自然地一次成交多格，实盘与回测完全一致。
 *
 * <h3>为什么格线固定</h3>
 * 本类生成的格线只由参数（lower/upper/grids/mode）决定，**不随成交漂移**——
 * 这是 V5.27 统一口径的结果：旧网格策略"实盘固定锚点、回测滚动锚点"两边不一致，回测调出的参数套不到实盘。
 * 需要跟随趋势时用 {@link #shiftedUp(int)} 显式整体上移（破上沿后的移动网格）。
 */
public final class GridLevels {

    /** 格位：跌破下沿（区间外下方） */
    public static final int BELOW_LOWER = -1;

    /** 等差模式标识 */
    public static final String MODE_ARITHMETIC = "arithmetic";

    /** 等比模式标识 */
    public static final String MODE_GEOMETRIC = "geometric";

    /** 上移搜索的最大格数（防御性上限，避免极端参数下死循环） */
    private static final int MAX_SHIFT_STEPS = 1000;

    private final BigDecimal lower;

    private final BigDecimal upper;

    private final int grids;

    /** true=等比（每格固定百分比），false=等差（每格固定价差） */
    private final boolean geometric;

    /** 等差步长（geometric=true 时无意义） */
    private final BigDecimal step;

    /** 等比公比（geometric=false 时无意义） */
    private final BigDecimal ratio;

    private GridLevels(BigDecimal lower, BigDecimal upper, int grids, boolean geometric,
                       BigDecimal step, BigDecimal ratio) {
        this.lower = lower;
        this.upper = upper;
        this.grids = grids;
        this.geometric = geometric;
        this.step = step;
        this.ratio = ratio;
    }

    /**
     * 构建格线。
     *
     * @param lower     下沿（须 &gt; 0）
     * @param upper     上沿（须 &gt; lower）
     * @param grids     格数（须 ≥ 2）
     * @param geometric true=等比，false=等差
     * @return 格线
     */
    public static GridLevels of(BigDecimal lower, BigDecimal upper, int grids, boolean geometric) {
        if (lower == null || upper == null || lower.compareTo(BigDecimal.ZERO) <= 0
                || upper.compareTo(lower) <= 0) {
            throw new BizException("网格上下沿须满足 0 < 下沿 < 上沿");
        }
        if (grids < 2) {
            throw new BizException("网格格数至少为 2");
        }
        if (geometric) {
            // 公比 = (upper/lower)^(1/grids)：由数学幂运算得出，8 位小数足够（单格误差 < 0.001%）
            BigDecimal ratio = BigDecimal.valueOf(
                    Math.pow(upper.doubleValue() / lower.doubleValue(), 1.0 / grids))
                    .setScale(8, RoundingMode.HALF_UP);
            return new GridLevels(lower, upper, grids, true, null, ratio);
        }
        BigDecimal step = upper.subtract(lower).divide(BigDecimal.valueOf(grids), 6, RoundingMode.HALF_UP);
        return new GridLevels(lower, upper, grids, false, step, null);
    }

    /** 第 k 条格线（0=下沿 … grids=上沿） */
    public BigDecimal level(int k) {
        if (k <= 0) {
            return lower;
        }
        if (k >= grids) {
            return upper;
        }
        if (geometric) {
            return lower.multiply(ratio.pow(k)).setScale(4, RoundingMode.HALF_UP);
        }
        return lower.add(step.multiply(BigDecimal.valueOf(k))).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * 价格所处的格位（见类注释语义）：区间外下方 -1、区间外上方 grids、区间内 0..grids-1。
     *
     * @param price 价格（null 或 ≤0 视为下沿之下）
     * @return 格位
     */
    public int position(BigDecimal price) {
        if (price == null || price.compareTo(lower) < 0) {
            return BELOW_LOWER;
        }
        if (price.compareTo(upper) >= 0) {
            return grids;
        }
        for (int k = grids - 1; k >= 0; k--) {
            if (price.compareTo(level(k)) >= 0) {
                return k;
            }
        }
        return BELOW_LOWER;
    }

    /**
     * 整体上移格线：把区间抬高到"现价重新落在区间内"为止（破上沿后的移动网格）。
     *
     * @param price 当前价
     * @return 上移后的新格线；现价已在区间内时返回自身
     */
    public GridLevels shiftedUp(BigDecimal price) {
        if (price == null) {
            return this;
        }
        int steps = 1;
        while (steps <= MAX_SHIFT_STEPS && price.compareTo(levelTopOf(steps)) >= 0) {
            steps++;
        }
        return shift(steps);
    }

    /**
     * 上移 steps 格（等差 = 上沿/下沿各 +steps×步长；等比 = 各 ×公比^steps）。
     *
     * @param steps 上移格数（≤0 返回自身）
     * @return 新格线
     */
    public GridLevels shift(int steps) {
        if (steps <= 0) {
            return this;
        }
        BigDecimal shiftValue = geometric
                ? ratio.pow(steps)
                : step.multiply(BigDecimal.valueOf(steps));
        BigDecimal newLower = geometric ? lower.multiply(shiftValue).setScale(4, RoundingMode.HALF_UP)
                : lower.add(shiftValue).setScale(4, RoundingMode.HALF_UP);
        BigDecimal newUpper = geometric ? upper.multiply(shiftValue).setScale(4, RoundingMode.HALF_UP)
                : upper.add(shiftValue).setScale(4, RoundingMode.HALF_UP);
        return of(newLower, newUpper, grids, geometric);
    }

    /**
     * 价格低于下沿时距下沿还有几格（向上取整）：刚跌破=1，再跌满一格=2……
     * 供"跌破下沿后继续按格买入"（breakdownMode=buy）计算额外买入格数。
     *
     * @param price 价格
     * @return 区间下方的格数（不低于下沿返回 0）
     */
    public int stepsBelow(BigDecimal price) {
        if (price == null || price.compareTo(lower) >= 0) {
            return 0;
        }
        if (geometric) {
            double steps = Math.log(lower.doubleValue() / price.doubleValue()) / Math.log(ratio.doubleValue());
            return (int) Math.ceil(steps);
        }
        return lower.subtract(price).divide(step, 0, RoundingMode.CEILING).intValue();
    }

    /** 上移 steps 格后的上沿（用于确定"要抬多少格现价才落回区间内"） */
    private BigDecimal levelTopOf(int steps) {
        if (geometric) {
            return upper.multiply(ratio.pow(steps)).setScale(4, RoundingMode.HALF_UP);
        }
        return upper.add(step.multiply(BigDecimal.valueOf(steps))).setScale(4, RoundingMode.HALF_UP);
    }

    public BigDecimal lower() {
        return lower;
    }

    public BigDecimal upper() {
        return upper;
    }

    public int grids() {
        return grids;
    }

    public boolean geometric() {
        return geometric;
    }

    /** 每格价差（等差）；等比返回上沿与下沿之差 ÷ 格数，仅供展示 */
    public BigDecimal step() {
        return geometric ? upper.subtract(lower).divide(BigDecimal.valueOf(grids), 4, RoundingMode.HALF_UP)
                : step;
    }

    /** 单格幅度（%）：相邻两格线的价差占比，供文案展示（等比模式取上下沿之差÷格数，仅作展示） */
    public BigDecimal stepPct() {
        return step().multiply(BigDecimal.valueOf(100)).divide(lower, 2, RoundingMode.HALF_UP);
    }
}
