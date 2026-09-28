package com.quant.strategy.grid;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import com.quant.common.exception.BizException;
import com.quant.strategy.core.BacktestAction;
import com.quant.strategy.core.BacktestState;
import com.quant.strategy.core.MarketDataSeries;
import com.quant.strategy.core.Signal;
import com.quant.strategy.core.Strategy;
import com.quant.strategy.core.StrategyContext;

import tools.jackson.databind.JsonNode;

/**
 * 网格策略通用实现（V5.27）：【红利网格】与【纳指网格】两个类型共用这一套规则，
 * 差异只体现在**默认参数**（前端表单里的初值：格宽、锚定点、破上沿行为、趋势/溢价闸门），
 * 用户可以逐项自己调，所以两个类型都不写死任何阈值。
 *
 * <h3>与旧【网格交易 GRID】的区别（旧策略一行未改，互不影响）</h3>
 * <ul>
 *   <li><b>格线固定</b>：格线由 lower/upper/grids/mode 一次性算出，不随成交漂移。
 *       旧 GRID 是"实盘固定锚点、回测滚动锚点"，两边口径不一致；新策略实盘与回测走同一个
 *       {@link #judge} 核心，回测调出来的参数可以直接套实盘。</li>
 *   <li><b>格位锚点 = 最近一次实际成交价</b>（实盘取 trade_flow，回测取引擎成交；
 *       没有任何成交时用上一根 bar 收盘）。因此"信号发了但没执行（无流水）"次日仍会提示，
 *       执行了（有流水）就不会重复提示——与用户 V5.9 口径天然一致。</li>
 *   <li><b>一天跨多格一次成交</b>：跳空跌 2% 穿 2 格就买 2 格份额（受 {@code maxGridsPerBar} 限幅），
 *       不再"一天只动一格"。</li>
 *   <li><b>仓位用底仓/满仓约束</b>：{@code baseShare} 卖出下限、{@code fullShare} 买入上限
 *       （与【震荡向上】同一套词汇），{@code initialShare} 仅回测首日建仓。</li>
 *   <li><b>逐格阶梯（金字塔/倒金字塔）</b>：{@code pyramidStep} 控制"每格的成交单位随格位线性增减"——
 *       0＝每格等量（普通网格）、正数＝越跌买越多且越涨卖越多（金字塔网格，底部重仓摊成本）、
 *       负数＝越跌买越少且越涨卖越少（倒金字塔网格，底部轻仓、留住筹码）。
 *       单位按**绝对格位**取值（买入随深度递增、卖出随高度递增，两向镜像配对），
 *       一天跨多格时按沿途各格**逐格累加**（不是"格数×每格"）。</li>
 * </ul>
 *
 * <h3>四道闸门（都是可调参数，0/空即关闭）</h3>
 * <ul>
 *   <li>{@code trendMaDays}：现价低于 N 日均线时禁止买入（卖出不受限），均线不足 N 根时闸门不生效并提示；</li>
 *   <li>{@code premiumBuyMaxPct} + {@code premiumStaleDays}：溢价率高于阈值禁止买入（跨境 ETF 的核心保护）；
 *       净值日滞后超过容忍天数（QDII 常见）时视为数据过期、不拦截并提示；</li>
 *   <li>{@code backtestPremiumPct}：回测没有溢价率历史（平台只存最新一天），空=回测跳过该闸门，
 *       填值=按该固定假设溢价率判断；</li>
 *   <li>{@code peBuyMax} / {@code peBuyMin} / {@code peBoostMultiplier}：跟踪指数 PE 高于上限禁买、
 *       低于下限时买入格数加倍（红利这类有 PE 数据的品种可用，纳指无 PE 数据源因此不生效）。</li>
 * </ul>
 *
 * <h3>破边界</h3>
 * <ul>
 *   <li>{@code breakoutMode}（涨破上沿）：{@code shift}=区间整体上移（移动网格，趋势品种）、
 *       {@code hold}=按格卖出到上沿容量后保留底仓（慢牛品种）、{@code clear}=按格卖出后清到只剩底仓；</li>
 *   <li>{@code breakdownMode}（跌破下沿）：{@code hold}=跌破下沿买 1 格后观望、
 *       {@code buy}=区间下方继续"每跌一格买一格"（受满仓上限约束）。</li>
 * </ul>
 */
public abstract class AbstractGridStrategy implements Strategy {

    /** 建议说明最长保留长度（signal_record.suggest_desc 列宽 255） */
    protected static final int DESC_MAX_LEN = 255;

    /** 网格参数：下沿 */
    protected static final String P_LOWER = "lower";

    /** 网格参数：上沿 */
    protected static final String P_UPPER = "upper";

    /** 网格参数：格数 */
    protected static final String P_GRIDS = "grids";

    /** 网格参数：等差/等比 */
    protected static final String P_MODE = "mode";

    /** 网格参数：每格单位（份额/金额） */
    protected static final String P_PER_GRID_MODE = "perGridMode";

    /** 网格参数：每格份额 */
    protected static final String P_SHARE_PER_GRID = "sharePerGrid";

    /** 网格参数：每格金额 */
    protected static final String P_AMOUNT_PER_GRID = "amountPerGrid";

    /** 网格参数：底仓份额（卖出下限） */
    protected static final String P_BASE_SHARE = "baseShare";

    /** 网格参数：满仓份额（买入上限） */
    protected static final String P_FULL_SHARE = "fullShare";

    /** 网格参数：回测首日建仓份额 */
    protected static final String P_INITIAL_SHARE = "initialShare";

    /** 网格参数：涨破上沿的行为 */
    protected static final String P_BREAKOUT_MODE = "breakoutMode";

    /** 网格参数：跌破下沿的行为 */
    protected static final String P_BREAKDOWN_MODE = "breakdownMode";

    /** 网格参数：单根 K 线最多成交格数（0=不限） */
    protected static final String P_MAX_GRIDS_PER_BAR = "maxGridsPerBar";

    /** 网格参数：趋势均线天数（0=关闭趋势闸门） */
    protected static final String P_TREND_MA_DAYS = "trendMaDays";

    /** 网格参数：溢价率买入上限（%，0=关闭溢价闸门） */
    protected static final String P_PREMIUM_BUY_MAX = "premiumBuyMaxPct";

    /** 网格参数：溢价率数据容忍滞后天数（QDII 净值公布滞后） */
    protected static final String P_PREMIUM_STALE_DAYS = "premiumStaleDays";

    /** 网格参数：回测假设溢价率（不填=回测跳过溢价闸门） */
    protected static final String P_BACKTEST_PREMIUM = "backtestPremiumPct";

    /** 网格参数：PE 买入上限（0=关闭） */
    protected static final String P_PE_BUY_MAX = "peBuyMax";

    /** 网格参数：PE 买入下限（低于它买入格数加倍；0=关闭） */
    protected static final String P_PE_BUY_MIN = "peBuyMin";

    /** 网格参数：PE 处于下限以下的买入格数倍数 */
    protected static final String P_PE_BOOST_MULTIPLIER = "peBoostMultiplier";

    /**
     * 网格参数：金字塔阶梯——每深一格的成交单位增减（与「每格单位」同单位）。
     * 0＝每格等量（普通网格）；**正数＝越跌买越多**（底部宽，金字塔网格）；
     * **负数＝越跌买越少**（底部窄，倒金字塔网格）。买卖用同一条阶梯，天然配对。
     */
    protected static final String P_PYRAMID_STEP = "pyramidStep";

    /** 每格单位：按份额 */
    protected static final String PER_GRID_SHARE = "share";

    /** 每格单位：按金额 */
    protected static final String PER_GRID_AMOUNT = "amount";

    /** 涨破上沿：区间上移（移动网格） */
    protected static final String BREAKOUT_SHIFT = "shift";

    /** 涨破上沿：按格卖出后保留底仓，区间不动 */
    protected static final String BREAKOUT_HOLD = "hold";

    /** 涨破上沿：按格卖出后清到只剩底仓 */
    protected static final String BREAKOUT_CLEAR = "clear";

    /** 跌破下沿：买入 1 格后观望 */
    protected static final String BREAKDOWN_HOLD = "hold";

    /** 跌破下沿：区间下方继续按格买入 */
    protected static final String BREAKDOWN_BUY = "buy";

    /** 默认每格份额（等金额模式忽略） */
    private static final BigDecimal DEFAULT_SHARE_PER_GRID = BigDecimal.valueOf(1000);

    /** 默认容忍的溢价率数据滞后天数 */
    private static final int DEFAULT_PREMIUM_STALE_DAYS = 3;

    /** 趋势均线天数上限（防御性：超过一年半的均线没有意义） */
    private static final int MAX_TREND_MA_DAYS = 250;

    /** 格数上限（防御性：格太密没有可操作性） */
    private static final int MAX_GRIDS = 200;

    /** 单格成交单位的下限（份额 0.01 份 / 金额 1 元）：阶梯递减到 0 时兜底，避免出现 0 或负数 */
    private static final BigDecimal MIN_LADDER_UNIT = new BigDecimal("0.01");

    /**
     * 是否本族网格类型（红利网格 / 纳指网格）——供信号窗口与回测预热换算统一识别，
     * 避免框架层散落多个硬编码字符串。
     *
     * @param strategyType 策略类型码
     * @return true=本族策略
     */
    public static boolean isGridType(String strategyType) {
        return DividendGridStrategy.TYPE.equals(strategyType) || NasdaqGridStrategy.TYPE.equals(strategyType)
                || PyramidGridStrategy.TYPE.equals(strategyType)
                || InversePyramidGridStrategy.TYPE.equals(strategyType);
    }

    @Override
    public void validateParams(JsonNode params) {
        BigDecimal lower = Strategy.dec(params, P_LOWER, BigDecimal.ZERO);
        BigDecimal upper = Strategy.dec(params, P_UPPER, BigDecimal.ZERO);
        if (lower.compareTo(BigDecimal.ZERO) <= 0 || upper.compareTo(lower) <= 0) {
            throw new BizException("网格上下沿须满足 0 < 下沿 < 上沿，当前 " + strip(lower) + " ~ " + strip(upper));
        }
        int grids = Strategy.intOr(params, P_GRIDS, 10);
        if (grids < 2 || grids > MAX_GRIDS) {
            throw new BizException("网格格数须在 2~" + MAX_GRIDS + " 之间，当前 " + grids);
        }
        checkOption(params, P_MODE, GridLevels.MODE_ARITHMETIC,
                new String[]{GridLevels.MODE_ARITHMETIC, GridLevels.MODE_GEOMETRIC}, "网格类型");
        String perGridMode = Strategy.strOr(params, P_PER_GRID_MODE, PER_GRID_SHARE);
        checkOption(params, P_PER_GRID_MODE, PER_GRID_SHARE,
                new String[]{PER_GRID_SHARE, PER_GRID_AMOUNT}, "每格单位");
        if (PER_GRID_AMOUNT.equals(perGridMode)) {
            if (Strategy.dec(params, P_AMOUNT_PER_GRID, BigDecimal.ZERO).compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException("每格金额须大于 0");
            }
        } else if (Strategy.dec(params, P_SHARE_PER_GRID, BigDecimal.ZERO).compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("每格份额须大于 0");
        }
        BigDecimal full = Strategy.dec(params, P_FULL_SHARE, BigDecimal.ZERO);
        BigDecimal baseShare = Strategy.dec(params, P_BASE_SHARE, BigDecimal.ZERO);
        if (full.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("满仓份额须大于 0（买入上限）");
        }
        if (baseShare.compareTo(BigDecimal.ZERO) < 0 || baseShare.compareTo(full) >= 0) {
            throw new BizException("底仓份额须在 0 ~ 满仓份额之间（不含满仓），当前 " + strip(baseShare));
        }
        BigDecimal initial = Strategy.dec(params, P_INITIAL_SHARE, BigDecimal.ZERO);
        if (initial.compareTo(BigDecimal.ZERO) < 0 || initial.compareTo(full) > 0) {
            throw new BizException("初始仓位份额须在 0 ~ 满仓份额之间，当前 " + strip(initial));
        }
        checkOption(params, P_BREAKOUT_MODE, BREAKOUT_HOLD,
                new String[]{BREAKOUT_SHIFT, BREAKOUT_HOLD, BREAKOUT_CLEAR}, "涨破上沿的处理方式");
        checkOption(params, P_BREAKDOWN_MODE, BREAKDOWN_HOLD,
                new String[]{BREAKDOWN_HOLD, BREAKDOWN_BUY}, "跌破下沿的处理方式");
        if (Strategy.intOr(params, P_MAX_GRIDS_PER_BAR, 0) < 0) {
            throw new BizException("单根 K 线最多成交格数不能为负（0=不限）");
        }
        int trendDays = Strategy.intOr(params, P_TREND_MA_DAYS, 0);
        if (trendDays < 0 || trendDays > MAX_TREND_MA_DAYS) {
            throw new BizException("趋势均线天数须在 0~" + MAX_TREND_MA_DAYS + " 之间（0=关闭趋势闸门），当前 " + trendDays);
        }
        if (Strategy.dec(params, P_PREMIUM_BUY_MAX, BigDecimal.ZERO).compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException("溢价率买入上限不能为负（0=关闭溢价闸门）");
        }
        if (Strategy.intOr(params, P_PREMIUM_STALE_DAYS, DEFAULT_PREMIUM_STALE_DAYS) < 0) {
            throw new BizException("溢价率数据容忍滞后天数不能为负");
        }
        if (params.has(P_BACKTEST_PREMIUM) && Strategy.dec(params, P_BACKTEST_PREMIUM, BigDecimal.ZERO)
                .compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException("回测假设溢价率不能为负（留空=回测跳过溢价闸门）");
        }
        if (Strategy.dec(params, P_PE_BUY_MAX, BigDecimal.ZERO).compareTo(BigDecimal.ZERO) < 0
                || Strategy.dec(params, P_PE_BUY_MIN, BigDecimal.ZERO).compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException("PE 买入上下限不能为负（0=关闭估值闸门）");
        }
        if (Strategy.dec(params, P_PE_BOOST_MULTIPLIER, BigDecimal.ONE).compareTo(BigDecimal.ONE) < 0) {
            throw new BizException("PE 低估时的买入格数倍数不能小于 1");
        }
        checkLadder(params, grids, perGridMode);
    }

    /**
     * 金字塔阶梯校验：每格增减幅度过大时，最深处那一格的成交单位会降到 0 以下（无法成交、语义也不清），
     * 这里提前用中文报错拦住，并给出可行的最大幅度。
     */
    private void checkLadder(JsonNode params, int grids, String perGridMode) {
        BigDecimal step = Strategy.dec(params, P_PYRAMID_STEP, BigDecimal.ZERO);
        if (step.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        BigDecimal base = PER_GRID_AMOUNT.equals(perGridMode)
                ? Strategy.dec(params, P_AMOUNT_PER_GRID, BigDecimal.ZERO)
                : Strategy.dec(params, P_SHARE_PER_GRID, BigDecimal.ZERO);
        BigDecimal deepest = base.add(step.multiply(BigDecimal.valueOf(grids - 1L)));
        if (deepest.compareTo(MIN_LADDER_UNIT) >= 0) {
            return;
        }
        BigDecimal limit = base.subtract(MIN_LADDER_UNIT)
                .divide(BigDecimal.valueOf(grids - 1L), 2, RoundingMode.DOWN);
        String unit = PER_GRID_AMOUNT.equals(perGridMode) ? "元" : "份";
        throw new BizException((step.compareTo(BigDecimal.ZERO) > 0 ? "每格递增" : "每格递减") + "幅度过大："
                + "按当前设置，第 " + grids + " 格的成交单位会降到 " + strip(deepest) + unit
                + "（须 ≥ 0.01" + unit + "）。请把每格增减幅度调到 " + strip(limit) + unit
                + " 以内（当前 " + strip(step) + "），或减少格数 / 提高首格份额。");
    }

    @Override
    public Signal generateSignal(StrategyContext context) {
        MarketDataSeries series = context.recentSeries();
        if (series.size() < 2) {
            return new Signal(Signal.HOLD, series.size() == 0 ? null : series.get(series.size() - 1).close(),
                    "行情数据不足（至少需要 2 根 K 线才能判断是否跨格）");
        }
        int index = series.size() - 1;
        BigDecimal price = series.get(index).close();
        // 格位锚点：最近一次实际成交价（trade_flow，只认买入/卖出）；没有成交时用上一根 bar 收盘，
        // 等价于"最后两根 K 线之间有没有跨格"——首次也能给出提示，且不依赖任何持久化状态
        BigDecimal anchor = context.lastTradePrice() != null ? context.lastTradePrice() : series.get(index - 1).close();
        BigDecimal premium = context.fund() == null ? null : context.fund().getPremiumRate();
        LocalDate premiumDate = context.fund() == null ? null : context.fund().getPremiumDate();
        GridDecision decision = judge(context.params(), series, index, nz(context.currentShares()), anchor,
                new GateState(premium, premiumDate, series.get(index).date()));
        return new Signal(decision.direction(), price, decision.reason());
    }

    @Override
    public BacktestAction decide(int index, MarketDataSeries data, BacktestState state) {
        JsonNode params = (JsonNode) state.getScratch().get("params");
        BigDecimal price = data.get(index).close();
        BigDecimal full = Strategy.dec(params, P_FULL_SHARE, BigDecimal.ZERO);
        // 回测首日建底仓（initialShare>0 时一次性），让回测从"已持有仓位"起步；0＝不建仓
        if (Boolean.TRUE != state.getScratch().get("initialDone")) {
            state.getScratch().put("initialDone", Boolean.TRUE);
            BigDecimal initial = Strategy.dec(params, P_INITIAL_SHARE, BigDecimal.ZERO).min(full);
            if (initial.compareTo(BigDecimal.ZERO) > 0) {
                state.getScratch().put("anchorPrice", price);
                return BacktestAction.buy(initial, "建立初始仓位 " + strip(initial) + " 份");
            }
        }
        BigDecimal anchor = (BigDecimal) state.getScratch().get("anchorPrice");
        if (anchor == null) {
            anchor = index > 0 ? data.get(index - 1).close() : price;
        }
        // 回测溢价率：参数给了假设值就按它判断，留空则跳过（平台只存最新一天的溢价率，历史无法回补）
        BigDecimal assumedPremium = backtestPremium(params);
        GridDecision decision = judge(params, data, index, state.getShares(), anchor,
                new GateState(assumedPremium, assumedPremium == null ? null : data.get(index).date(),
                        data.get(index).date()));
        if (Signal.HOLD.equals(decision.direction())) {
            return BacktestAction.hold();
        }
        BigDecimal delta = decision.targetShares().subtract(state.getShares()).abs()
                .setScale(2, RoundingMode.DOWN);
        if (delta.compareTo(BigDecimal.ZERO) <= 0) {
            return BacktestAction.hold();
        }
        // 成交即重置格位锚点（引擎次日按开盘价撮合，这里用决策日收盘价近似，与【震荡向上】同一口径）
        state.getScratch().put("anchorPrice", price);
        return Signal.BUY.equals(decision.direction())
                ? BacktestAction.buy(delta, decision.reason())
                : BacktestAction.sell(delta, decision.reason());
    }

    /**
     * 判定核心（实盘与回测共用）：格位换算 → 闸门 → 夹取 → 决策。
     *
     * @param params        策略参数
     * @param series        行情序列
     * @param index         决策所在 bar 下标
     * @param currentShares 当前持仓份额
     * @param anchorPrice   格位锚点价（最近一次实际成交价，无成交时为上一根收盘）
     * @param gate          闸门输入（溢价率及其净值日、当前 bar 日期）
     * @return 决策（HOLD = 不出信号）
     */
    protected GridDecision judge(JsonNode params, MarketDataSeries series, int index, BigDecimal currentShares,
                                 BigDecimal anchorPrice, GateState gate) {
        BigDecimal price = series.get(index).close();
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            return GridDecision.hold("行情数据不完整，无法判断");
        }
        BigDecimal full = Strategy.dec(params, P_FULL_SHARE, BigDecimal.ZERO);
        BigDecimal baseShare = Strategy.dec(params, P_BASE_SHARE, BigDecimal.ZERO);
        if (full.compareTo(BigDecimal.ZERO) <= 0) {
            return GridDecision.hold("满仓份额未配置，无法判断");
        }
        GridLevels base = GridLevels.of(
                Strategy.dec(params, P_LOWER, BigDecimal.ZERO),
                Strategy.dec(params, P_UPPER, BigDecimal.ZERO),
                Strategy.intOr(params, P_GRIDS, 10),
                GridLevels.MODE_GEOMETRIC.equals(Strategy.strOr(params, P_MODE, GridLevels.MODE_ARITHMETIC)));
        String breakoutMode = Strategy.strOr(params, P_BREAKOUT_MODE, BREAKOUT_HOLD);
        // 格位参考点：正常情况下用"最近一次实际成交价"；
        // 但当仓位已**贴住满仓或底仓**（某一方向彻底动不了）时，继续拿陈旧的成交价当参考会让网格永久卡死
        // ——实测 513300：底仓=初始仓位，价格一路涨到区间上方，卖出被底仓夹光、锚点却停在建仓价，
        // 于是既卖不出（贴底仓）也买不到（价格远在锚点之上），整段回测只有 1 笔建仓成交。
        // 故贴住上下限时改用上一根收盘作参考，网格照常"下跌买、上涨卖"，一旦成交即恢复用成交价。
        boolean positionPinned = currentShares.compareTo(full) >= 0 || currentShares.compareTo(baseShare) <= 0;
        BigDecimal reference = positionPinned && index > 0 ? series.get(index - 1).close() : anchorPrice;
        String pinnedNote = positionPinned
                ? (currentShares.compareTo(full) >= 0 ? "（持仓已达满仓，参考点随行情移动）"
                        : "（持仓已到底仓，参考点随行情移动）")
                : "";
        // 移动网格（shift）：区间整体上移到"现价与参考点中更高的那个"重新落回区间内为止（只涨不跌）。
        // 参考点必须用同一套格线定位，否则上破之后格位差会算错。
        BigDecimal referenceForShift = reference == null ? price : price.max(reference);
        GridLevels frame = BREAKOUT_SHIFT.equals(breakoutMode) && referenceForShift.compareTo(base.upper()) >= 0
                ? base.shiftedUp(referenceForShift) : base;
        int anchorPos = frame.position(reference == null ? price : reference);
        int pricePos = frame.position(price);
        int gridsCrossed = anchorPos - pricePos;
        // 跌破下沿后的处理：hold=只买"穿越下沿"那一格，之后停在区间外观望；
        // buy=把下沿之下也当成格子，每多跌一格再买一格（额外格数，受 maxGridsPerBar 与满仓上限约束）
        if (pricePos == GridLevels.BELOW_LOWER && BREAKDOWN_BUY.equals(Strategy.strOr(params, P_BREAKDOWN_MODE,
                BREAKDOWN_HOLD))) {
            int belowSteps = frame.stepsBelow(price);
            if (belowSteps > 1) {
                gridsCrossed += belowSteps - 1;
            }
        }
        String windowText = "网格 " + strip(frame.lower()) + "~" + strip(frame.upper()) + "（"
                + (frame.geometric() ? "等比" : "等差") + frame.grids() + " 格，每格约 "
                + strip(frame.stepPct()) + "%）";
        String moveText = "现价 " + strip(price) + (pricePos == GridLevels.BELOW_LOWER ? "（已跌破下沿）" : "")
                + " 位于第 " + pricePos + " 格，参考点 " + strip(reference)
                + " 位于第 " + anchorPos + " 格" + pinnedNote + "，";
        if (perGridShares(params, price).compareTo(BigDecimal.ZERO) <= 0) {
            return GridDecision.hold("每格份额/金额未配置或过小，无法给出可执行建议");
        }
        int cap = capGrids(params);
        // ① 涨破上沿且要求"清到只剩底仓"
        if (pricePos >= frame.grids() && BREAKOUT_CLEAR.equals(breakoutMode)) {
            BigDecimal target = currentShares.min(full).max(baseShare);
            if (target.compareTo(currentShares) >= 0) {
                return GridDecision.hold("现价已涨破网格上沿且持仓已在底仓 " + strip(baseShare) + " 份，无变化不提信号");
            }
            return new GridDecision(Signal.SELL, target, trim("现价 " + strip(price) + " 涨破网格上沿 "
                    + strip(frame.upper()) + "（清仓模式），建议卖出 " + strip(currentShares.subtract(target))
                    + " 份至底仓 " + strip(target) + " 份；" + windowText));
        }
        // ② 买入方向（含跌破下沿、区间下方继续按格买入）
        if (gridsCrossed > 0) {
            int buyGrids = cap == 0 ? gridsCrossed : Math.min(gridsCrossed, cap);
            String block = buyBlockReason(params, series, index, price, gate);
            String gateNote = gateSkipNote(params, series, index, gate);
            buyGrids = applyPeBoost(params, series.get(index).pe(), buyGrids);
            // 逐格累加：金字塔/倒金字塔每一格的成交单位不同（普通网格每格相同，累加结果等于"格数×每格"）
            BigDecimal want = ladderTotal(params, price, anchorPos, pricePos, true);
            BigDecimal target = currentShares.add(want).min(full);
            String trigger = "下跌跨 " + gridsCrossed + " 格" + (buyGrids < gridsCrossed ? "（按单根上限取 "
                    + buyGrids + " 格）" : "") + "，";
            if (target.compareTo(currentShares) <= 0) {
                return GridDecision.hold(moveText + trigger + "但持仓已达满仓上限 " + strip(full) + " 份，"
                        + "无变化不提信号；" + windowText);
            }
            if (block != null) {
                return GridDecision.hold(moveText + trigger + "本应买入 " + strip(target.subtract(currentShares))
                        + " 份，但" + block + "，今日不提供买入建议；" + windowText);
            }
            return new GridDecision(Signal.BUY, target, trim(moveText + trigger + "建议买入 "
                    + strip(target.subtract(currentShares)) + " 份至 " + strip(target) + " 份" + gateNote
                    + "；" + windowText));
        }
        // ③ 卖出方向（跨格上涨）
        if (gridsCrossed < 0) {
            int sellGrids = cap == 0 ? -gridsCrossed : Math.min(-gridsCrossed, cap);
            BigDecimal want = ladderTotal(params, price, anchorPos, pricePos, false);
            BigDecimal target = currentShares.subtract(want).max(baseShare);
            if (target.compareTo(currentShares) >= 0) {
                return GridDecision.hold(moveText + "上涨跨 " + sellGrids + " 格，但持仓已到底仓下限 "
                        + strip(baseShare) + " 份，无变化不提信号；" + windowText);
            }
            return new GridDecision(Signal.SELL, target, trim(moveText + "上涨跨 " + sellGrids + " 格，建议卖出 "
                    + strip(currentShares.subtract(target)) + " 份至 " + strip(target) + " 份；" + windowText));
        }
        return GridDecision.hold(moveText + "未跨格，继续持有等待触格；" + windowText);
    }

    /**
     * 买入闸门：返回第一个拦截原因（null = 放行）。顺序为趋势 → 溢价率 → 估值。
     * 数据不足（均线未成形、无溢价率、无 PE）的闸门一律**不拦截**，只在提示里说明。
     */
    private String buyBlockReason(JsonNode params, MarketDataSeries series, int index, BigDecimal price,
                                  GateState gate) {
        int trendDays = Strategy.intOr(params, P_TREND_MA_DAYS, 0);
        BigDecimal ma = ma(series, index, trendDays);
        if (trendDays > 0 && ma != null && price.compareTo(ma) < 0) {
            return "现价低于 " + trendDays + " 日均线 " + strip(ma) + "（趋势闸门：跌势中不接飞刀）";
        }
        String premiumBlock = premiumBlockReason(params, gate);
        if (premiumBlock != null) {
            return premiumBlock;
        }
        BigDecimal pe = series.get(index).pe();
        BigDecimal peMax = Strategy.dec(params, P_PE_BUY_MAX, BigDecimal.ZERO);
        if (peMax.compareTo(BigDecimal.ZERO) > 0 && pe != null && pe.compareTo(peMax) > 0) {
            return "跟踪指数 PE " + strip(pe) + " 高于买入上限 " + strip(peMax) + "（估值闸门）";
        }
        return null;
    }

    /** 溢价率闸门（跨境 ETF 的核心保护）：数据过期视为不拦截 */
    private String premiumBlockReason(JsonNode params, GateState gate) {
        BigDecimal max = Strategy.dec(params, P_PREMIUM_BUY_MAX, BigDecimal.ZERO);
        if (max.compareTo(BigDecimal.ZERO) <= 0 || gate.premiumPct() == null || premiumStale(params, gate)) {
            return null;
        }
        return gate.premiumPct().compareTo(max) > 0
                ? "溢价率 " + strip(gate.premiumPct()) + "% 高于买入上限 " + strip(max)
                        + "%（高溢价买入等于先亏一笔溢价）"
                : null;
    }

    /** 闸门数据缺失/过期的提示文案（拼进买入建议里，让用户知道哪道闸门这次没生效） */
    private String gateSkipNote(JsonNode params, MarketDataSeries series, int index, GateState gate) {
        StringBuilder note = new StringBuilder();
        int trendDays = Strategy.intOr(params, P_TREND_MA_DAYS, 0);
        if (trendDays > 0 && ma(series, index, trendDays) == null) {
            note.append("；趋势闸门因均线不足 ").append(trendDays).append(" 根未启用");
        }
        BigDecimal premiumMax = Strategy.dec(params, P_PREMIUM_BUY_MAX, BigDecimal.ZERO);
        if (premiumMax.compareTo(BigDecimal.ZERO) > 0) {
            if (gate.premiumPct() == null) {
                note.append("；溢价闸门因无溢价率数据未启用");
            } else if (premiumStale(params, gate)) {
                note.append("；溢价闸门因溢价率数据滞后超过 ")
                        .append(Strategy.intOr(params, P_PREMIUM_STALE_DAYS, DEFAULT_PREMIUM_STALE_DAYS))
                        .append(" 天未启用");
            }
        }
        return note.toString();
    }

    /** 溢价率是否已过期（净值日距当前 bar 超过容忍天数；QDII 净值常见 T+1~T+2） */
    private boolean premiumStale(JsonNode params, GateState gate) {
        if (gate.premiumDate() == null || gate.barDate() == null) {
            return false;
        }
        long lag = ChronoUnit.DAYS.between(gate.premiumDate(), gate.barDate());
        return lag > Strategy.intOr(params, P_PREMIUM_STALE_DAYS, DEFAULT_PREMIUM_STALE_DAYS);
    }

    /** PE 低于下限时买入格数加倍（估值越便宜买得越多） */
    private int applyPeBoost(JsonNode params, BigDecimal pe, int buyGrids) {
        BigDecimal peMin = Strategy.dec(params, P_PE_BUY_MIN, BigDecimal.ZERO);
        BigDecimal multiplier = Strategy.dec(params, P_PE_BOOST_MULTIPLIER, BigDecimal.ONE);
        if (peMin.compareTo(BigDecimal.ZERO) <= 0 || pe == null || pe.compareTo(peMin) >= 0
                || multiplier.compareTo(BigDecimal.ONE) <= 0) {
            return buyGrids;
        }
        return BigDecimal.valueOf(buyGrids).multiply(multiplier).setScale(0, RoundingMode.DOWN).intValue();
    }

    /** 每格成交份额：按份额直接取；按金额则用"每格金额 ÷ 现价"折算（向下取整到 2 位） */
    private BigDecimal perGridShares(JsonNode params, BigDecimal price) {
        if (PER_GRID_AMOUNT.equals(Strategy.strOr(params, P_PER_GRID_MODE, PER_GRID_SHARE))) {
            BigDecimal amount = Strategy.dec(params, P_AMOUNT_PER_GRID, BigDecimal.ZERO);
            return amount.compareTo(BigDecimal.ZERO) <= 0 ? BigDecimal.ZERO
                    : amount.divide(price, 2, RoundingMode.DOWN);
        }
        BigDecimal share = Strategy.dec(params, P_SHARE_PER_GRID, DEFAULT_SHARE_PER_GRID);
        return share.compareTo(BigDecimal.ZERO) <= 0 ? BigDecimal.ZERO : share;
    }

    /**
     * 某一格位上的成交单位（份额）——金字塔阶梯按**格位**（而不是"距上次成交几格"）取值：
     * <pre>
     *   买入：单位 = 每格份额 + 每格增减 × (grids − 格位)     ← 越靠近下沿越大（金字塔）
     *   卖出：单位 = 每格份额 + 每格增减 × 格位               ← 越靠近上沿越大（与买入镜像配对）
     * </pre>
     * 为什么按格位而不是按"距上次成交"：参考点每笔成交后都会前移，按距离算的话每次跨格数永远是 1、
     * 阶梯根本体现不出来（实测金字塔/倒金字塔与普通网格成交份额完全一样）。按绝对格位取值后，
     * "越跌买越多/越少"才真正落在成交明细上；买卖两向镜像，所以一次完整的上下往返买卖总量相等（网格依然配对）。
     *
     * <p>每格增减 = 0 时退化为普通网格（每格等量）。单位按 {@link #MIN_LADDER_UNIT} 兜底，不会出现 0 或负数。
     *
     * @param position 格位（0=下沿 … grids=上沿）
     * @param buy      true=买入方向（从下沿往深处放大），false=卖出方向（往上沿放大）
     */
    private BigDecimal levelShares(JsonNode params, BigDecimal price, int position, boolean buy) {
        BigDecimal base = perGridShares(params, price);
        BigDecimal step = Strategy.dec(params, P_PYRAMID_STEP, BigDecimal.ZERO);
        if (step.compareTo(BigDecimal.ZERO) == 0) {
            return base;
        }
        int grids = Strategy.intOr(params, P_GRIDS, 10);
        int bounded = Math.max(0, Math.min(position, grids));
        int distance = buy ? grids - bounded : bounded;
        BigDecimal unit = base.add(step.multiply(BigDecimal.valueOf(distance)));
        if (unit.compareTo(MIN_LADDER_UNIT) < 0) {
            return MIN_LADDER_UNIT;
        }
        if (PER_GRID_AMOUNT.equals(Strategy.strOr(params, P_PER_GRID_MODE, PER_GRID_SHARE))) {
            // 等金额模式：阶梯作用于"金额"，再换算成份额
            BigDecimal amount = Strategy.dec(params, P_AMOUNT_PER_GRID, BigDecimal.ZERO)
                    .add(step.multiply(BigDecimal.valueOf(distance)));
            if (amount.compareTo(MIN_LADDER_UNIT) < 0) {
                amount = MIN_LADDER_UNIT;
            }
            return amount.divide(price, 2, RoundingMode.DOWN);
        }
        return unit;
    }

    /**
     * 一次跨越多个格位的合计成交份额：对沿途**每一个落到的格位**按阶梯取值再累加。
     *
     * @param fromPosition 起点格位（独占）
     * @param toPosition   终点格位（含）
     * @param buy          true=买入（向下跨越），false=卖出（向上跨越）
     */
    private BigDecimal ladderTotal(JsonNode params, BigDecimal price, int fromPosition, int toPosition, boolean buy) {
        BigDecimal total = BigDecimal.ZERO;
        int stepDirection = buy ? -1 : 1;
        for (int p = fromPosition; p != toPosition; p += stepDirection) {
            int level = p + stepDirection;
            total = total.add(levelShares(params, price, level, buy));
        }
        return total;
    }

    /** 单根 K 线成交格数上限（0=不限） */
    private int capGrids(JsonNode params) {
        return Math.max(Strategy.intOr(params, P_MAX_GRIDS_PER_BAR, 0), 0);
    }

    /** 回测假设溢价率（未配置返回 null = 回测跳过溢价闸门） */
    private BigDecimal backtestPremium(JsonNode params) {
        return params.has(P_BACKTEST_PREMIUM) ? Strategy.dec(params, P_BACKTEST_PREMIUM, BigDecimal.ZERO) : null;
    }

    /**
     * 简单移动平均（含 index 这根，向前取 days 根）。
     * 不足 days 根或有效收盘不足返回 null —— 调用方按"闸门不生效"处理（与估值百分位同一约定）。
     */
    private BigDecimal ma(MarketDataSeries series, int index, int days) {
        if (days <= 0 || index + 1 < days) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        for (int i = index - days + 1; i <= index; i++) {
            BigDecimal close = series.get(i).close();
            if (close != null) {
                sum = sum.add(close);
                count++;
            }
        }
        return count < days ? null : sum.divide(BigDecimal.valueOf(days), 4, RoundingMode.HALF_UP);
    }

    /** 枚举型参数校验（值不在集合内时给中文报错） */
    private void checkOption(JsonNode params, String field, String defaultValue, String[] allowed, String label) {
        String value = Strategy.strOr(params, field, defaultValue);
        for (String option : allowed) {
            if (option.equals(value)) {
                return;
            }
        }
        throw new BizException(label + "只能是 " + String.join(" / ", allowed) + "，当前 " + value);
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
     * 闸门输入。
     *
     * @param premiumPct  溢价率（%）；null = 无数据（闸门跳过）
     * @param premiumDate 溢价率对应的净值日（判断数据是否滞后）
     * @param barDate     当前决策 bar 的日期
     */
    protected record GateState(BigDecimal premiumPct, LocalDate premiumDate, LocalDate barDate) {
    }

    /**
     * 一次判定的结果。
     *
     * @param direction    BUY / SELL / HOLD
     * @param targetShares 目标持仓份额（HOLD 时无意义）
     * @param reason       建议说明
     */
    protected record GridDecision(String direction, BigDecimal targetShares, String reason) {

        static GridDecision hold(String reason) {
            return new GridDecision(Signal.HOLD, BigDecimal.ZERO, reason);
        }
    }
}
