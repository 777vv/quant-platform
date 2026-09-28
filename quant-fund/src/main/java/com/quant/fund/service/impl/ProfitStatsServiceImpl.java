package com.quant.fund.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.quant.common.exception.BizException;
import com.quant.common.util.JsonUtils;
import com.quant.fund.client.EastmoneyClient;
import com.quant.fund.dto.AssetSummaryVO;
import com.quant.fund.dto.DashboardOverviewVO;
import com.quant.fund.dto.HoldingVO;
import com.quant.fund.dto.LastQuote;
import com.quant.fund.dto.ProfitCurveVO;
import com.quant.fund.entity.FundBasic;
import com.quant.fund.entity.FundEtfKline;
import com.quant.fund.entity.FundNav;
import com.quant.fund.entity.TradeFlow;
import com.quant.fund.enums.FundTypeEnum;
import com.quant.fund.enums.TradeTypeEnum;
import com.quant.fund.mapper.FundBasicMapper;
import com.quant.fund.mapper.FundEtfKlineMapper;
import com.quant.fund.mapper.FundNavMapper;
import com.quant.fund.mapper.TradeFlowMapper;
import com.quant.fund.service.CashAccountingService;
import com.quant.fund.service.FundQueryService;
import com.quant.fund.service.ProfitStatsService;
import com.quant.fund.service.TradeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import tools.jackson.core.type.TypeReference;

/**
 * 收益统计引擎实现（FR1，M4-01）。
 * 口径说明：
 * 1) 净投入 = 累计买入(含费) - 累计卖出(净额) - 累计分红(净额)；
 * 2) 某日累计收益 = 当日持仓市值 - 当日净投入（该恒等式天然吸收了期间申赎的现金流影响）；
 * 3) 市值估值：ETF 用前复权收盘价、场外用单位净值；前复权会使历史中间点略微平滑（分红除权修正），
 *    但区间终点与真实一致，满足个人记账精度要求；
 * 4) 现金分红在摊薄成本法下同时计入已实现收益并冲减成本，与实际除权日净值下跌相互抵消，无重复计利。
 */
@Service
public class ProfitStatsServiceImpl implements ProfitStatsService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProfitStatsServiceImpl.class);

    /** 沪深300 指数（东财 secid 前缀 1 = 上交所） */
    private static final int BENCH_MARKET = 1;

    /** 沪深300 指数代码（收益曲线基准） */
    private static final String BENCH_CODE = "000300";

    /** 基准日 K 的 Redis 缓存键（值为 List&lt;BenchPoint&gt; JSON） */
    private static final String BENCH_CACHE_KEY = "quote:index:kline:000300";

    /** 基准缓存时长：6 小时（日 K 数据日内不变） */
    private static final Duration BENCH_CACHE_TTL = Duration.ofHours(6);

    /**
     * 基准拉取降级标记：快速通道也失败时置位并保留 10 分钟。
     * 期间直接返回空基准（曲线不含基准线），不再每次打开仪表盘都去撞一次被封堵的数据源。
     */
    private static final String BENCH_DEGRADED_KEY = "quote:benchmark:degraded";

    private static final Duration BENCH_DEGRADED_TTL = Duration.ofMinutes(10);

    /** 前向填充时额外向前多加载的天数，保证区间首日有价格基准 */
    private static final int FILL_LOOKBACK_DAYS = 14;

    /** 计算本月/本年收益时向区间起点前多回看的月数（覆盖长假，确保能找到上期末数据日） */
    private static final int FIRST_PERIOD_LOOKBACK_MONTHS = 3;

    private final FundBasicMapper fundBasicMapper;

    private final FundEtfKlineMapper klineMapper;

    private final FundNavMapper navMapper;

    private final TradeFlowMapper tradeFlowMapper;

    private final FundQueryService fundQueryService;

    private final TradeService tradeService;

    private final CashAccountingService cashAccountingService;

    private final EastmoneyClient eastmoneyClient;

    private final StringRedisTemplate redisTemplate;

    public ProfitStatsServiceImpl(FundBasicMapper fundBasicMapper, FundEtfKlineMapper klineMapper,
                                  FundNavMapper navMapper, TradeFlowMapper tradeFlowMapper,
                                  FundQueryService fundQueryService, TradeService tradeService,
                                  CashAccountingService cashAccountingService,
                                  EastmoneyClient eastmoneyClient, StringRedisTemplate redisTemplate) {
        this.fundBasicMapper = fundBasicMapper;
        this.klineMapper = klineMapper;
        this.navMapper = navMapper;
        this.tradeFlowMapper = tradeFlowMapper;
        this.fundQueryService = fundQueryService;
        this.tradeService = tradeService;
        this.cashAccountingService = cashAccountingService;
        this.eastmoneyClient = eastmoneyClient;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public AssetSummaryVO summary() {
        List<HoldingVO> holdings = tradeService.holdings();
        int watchCount = Math.toIntExact(fundBasicMapper.selectCount(
                new LambdaQueryWrapper<FundBasic>().eq(FundBasic::getStatus, 1)));
        BigDecimal marketValue = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal dayPnl = BigDecimal.ZERO;
        BigDecimal floating = BigDecimal.ZERO;
        BigDecimal realized = BigDecimal.ZERO;
        for (HoldingVO holding : holdings) {
            marketValue = marketValue.add(nvl(holding.marketValue()));
            totalCost = totalCost.add(nvl(holding.avgCostPrice()).multiply(nvl(holding.totalShare())));
            dayPnl = dayPnl.add(nvl(holding.dayPnl()));
            floating = floating.add(nvl(holding.floatingPnl()));
            realized = realized.add(nvl(holding.realizedPnl()));
        }
        BigDecimal totalPnl = floating.add(realized);
        // 现金余额与净投入统一由 CashAccountingService 计算（与转出额度校验共用同一口径）【V1.9 ㊾】
        BigDecimal netInvested = cashAccountingService.netInvested();
        BigDecimal cash = cashAccountingService.cashBalance();
        BigDecimal totalAssets = marketValue.add(cash);
        BigDecimal totalPnlPct = netInvested.compareTo(BigDecimal.ZERO) > 0
                ? totalPnl.multiply(BigDecimal.valueOf(100)).divide(netInvested, 2, RoundingMode.HALF_UP) : null;
        BigDecimal floatingPct = totalCost.compareTo(BigDecimal.ZERO) > 0
                ? floating.multiply(BigDecimal.valueOf(100)).divide(totalCost, 2, RoundingMode.HALF_UP) : null;
        PeriodPnl month = periodPnl(LocalDate.now().withDayOfMonth(1));
        PeriodPnl year = periodPnl(LocalDate.of(LocalDate.now().getYear(), 1, 1));
        return new AssetSummaryVO(money(totalAssets), money(cash), money(marketValue), money(totalCost), money(dayPnl),
                money(floating), floatingPct, money(realized), money(totalPnl), totalPnlPct, weekPnl(),
                month.pnl(), month.pct(), year.pnl(), year.pct(),
                holdings.size(), watchCount);
    }

    /**
     * 区间收益（本月/本年）：以区间起始日之前最后一个数据日为基准，
     * 收益率分母取基准日的持仓市值（而非净投入），更贴近"这段时间资产涨了多少"的直觉。
     */
    private PeriodPnl periodPnl(LocalDate periodStart) {
        List<FundBasic> funds = tradedFunds();
        if (funds.isEmpty()) {
            return new PeriodPnl(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), null);
        }
        LocalDate seriesStart = periodStart.minusMonths(FIRST_PERIOD_LOOKBACK_MONTHS);
        SeriesData series = computeSeries(seriesStart, funds);
        if (series.dates().isEmpty()) {
            return new PeriodPnl(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), null);
        }
        int latestIndex = series.dates().size() - 1;
        BigDecimal basePnl = null;
        BigDecimal baseMarketValue = null;
        for (int i = 0; i < series.dates().size(); i++) {
            if (LocalDate.parse(series.dates().get(i)).isBefore(periodStart)) {
                basePnl = series.pnl().get(i);
                baseMarketValue = series.marketValues().get(i);
            } else {
                break;
            }
        }
        BigDecimal pnl = basePnl == null ? series.pnl().get(latestIndex)
                : series.pnl().get(latestIndex).subtract(basePnl);
        BigDecimal pct = baseMarketValue == null || baseMarketValue.compareTo(BigDecimal.ZERO) <= 0 ? null
                : pnl.multiply(BigDecimal.valueOf(100)).divide(baseMarketValue, 2, RoundingMode.HALF_UP);
        return new PeriodPnl(pnl.setScale(2, RoundingMode.HALF_UP), pct);
    }

    @Override
    public ProfitCurveVO curve(String range) {
        LocalDate rangeStart = rangeStart(range);
        List<FundBasic> tradedFunds = tradedFunds();
        if (tradedFunds.isEmpty()) {
            return new ProfitCurveVO(rangeStart, LocalDate.now(), List.of(), List.of(), List.of(), List.of(), false);
        }
        LocalDate minTradeDate = tradeFlowMapper.selectList(new LambdaQueryWrapper<TradeFlow>()
                        .in(TradeFlow::getFundCode, tradedFunds.stream().map(FundBasic::getFundCode).toList()))
                .stream().map(TradeFlow::getTradeDate).min(Comparator.naturalOrder()).orElse(rangeStart);
        // 有效起点不早于第一笔流水，避免曲线拖出长零值尾巴
        LocalDate start = rangeStart.isAfter(minTradeDate) ? rangeStart : minTradeDate;
        SeriesData series = computeSeries(start, tradedFunds);
        if (series.dates().isEmpty()) {
            return new ProfitCurveVO(start, LocalDate.now(), List.of(), List.of(), List.of(), List.of(), false);
        }
        return new ProfitCurveVO(start, LocalDate.parse(series.dates().get(series.dates().size() - 1)),
                series.dates(), series.pnl(), benchmarkPct(series.dates(), start),
                monthlyOf(series.dates(), series.pnl()), true);
    }

    @Override
    public List<DashboardOverviewVO.MoverItem> movers7d() {
        List<FundBasic> funds = fundBasicMapper.selectList(
                new LambdaQueryWrapper<FundBasic>().eq(FundBasic::getStatus, 1));
        LocalDate target = LocalDate.now().minusDays(7);
        List<DashboardOverviewVO.MoverItem> movers = new ArrayList<>();
        for (FundBasic fund : funds) {
            LastQuote quote = fundQueryService.lastQuote(fund);
            BigDecimal past = priceOnOrBefore(fund, target);
            if (quote == null || quote.price() == null || past == null || past.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            movers.add(new DashboardOverviewVO.MoverItem(fund.getFundCode(), fund.getFundName(),
                    quote.price().subtract(past).multiply(BigDecimal.valueOf(100))
                            .divide(past, 2, RoundingMode.HALF_UP)));
        }
        movers.sort(Comparator.comparing(DashboardOverviewVO.MoverItem::changePct7d).reversed());
        return movers;
    }

    /**
     * 日收益序列：逐日重建 市值 / 净投入 / 累计收益。
     * 份额与净投入沿流水指针累加，价格用 TreeMap 前向填充（当日无行情沿用最近一日）。
     */
    private SeriesData computeSeries(LocalDate start, List<FundBasic> funds) {
        List<FundState> states = new ArrayList<>();
        TreeSet<LocalDate> axis = new TreeSet<>();
        LocalDate loadFrom = start.minusDays(FILL_LOOKBACK_DAYS);
        for (FundBasic fund : funds) {
            List<TradeFlow> flows = tradeFlowMapper.selectList(new LambdaQueryWrapper<TradeFlow>()
                    .eq(TradeFlow::getFundCode, fund.getFundCode())
                    .orderByAsc(TradeFlow::getTradeDate).orderByAsc(TradeFlow::getId));
            if (flows.isEmpty()) {
                continue;
            }
            TreeMap<LocalDate, BigDecimal> prices = priceMapOf(fund, loadFrom);
            // 首笔交易早于本地行情起点时，以成交价补种子价，避免区间首日无价可填
            for (TradeFlow flow : flows) {
                if (flow.getPrice() != null && flow.getPrice().compareTo(BigDecimal.ZERO) > 0
                        && (prices.isEmpty() || flow.getTradeDate().isBefore(prices.firstKey()))) {
                    prices.put(flow.getTradeDate(), flow.getPrice());
                }
            }
            prices.tailMap(start, true).keySet().forEach(axis::add);
            states.add(new FundState(prices, flows));
        }
        List<String> dates = new ArrayList<>(axis.size());
        List<BigDecimal> pnl = new ArrayList<>(axis.size());
        List<BigDecimal> marketValues = new ArrayList<>(axis.size());
        for (LocalDate date : axis) {
            BigDecimal marketValue = BigDecimal.ZERO;
            BigDecimal netInvested = BigDecimal.ZERO;
            for (FundState state : states) {
                Map.Entry<LocalDate, BigDecimal> priceEntry = state.prices.floorEntry(date);
                if (priceEntry == null) {
                    continue;
                }
                state.applyFlowsUpTo(date);
                marketValue = marketValue.add(state.share.multiply(priceEntry.getValue()));
                netInvested = netInvested.add(state.netInvested);
            }
            dates.add(date.toString());
            marketValues.add(marketValue.setScale(2, RoundingMode.HALF_UP));
            pnl.add(marketValue.subtract(netInvested).setScale(2, RoundingMode.HALF_UP));
        }
        return new SeriesData(dates, pnl, marketValues);
    }

    /** 近 7 日收益 = 今日累计收益 - 7 个自然日前（或其后首个数据日）累计收益 */
    private BigDecimal weekPnl() {
        List<FundBasic> funds = tradedFunds();
        if (funds.isEmpty()) {
            return BigDecimal.ZERO;
        }
        SeriesData series = computeSeries(LocalDate.now().minusDays(14), funds);
        if (series.dates().isEmpty()) {
            return BigDecimal.ZERO;
        }
        LocalDate target = LocalDate.now().minusDays(7);
        BigDecimal latest = series.pnl().get(series.dates().size() - 1);
        BigDecimal base = latest;
        for (int i = 0; i < series.dates().size(); i++) {
            if (!LocalDate.parse(series.dates().get(i)).isBefore(target)) {
                base = series.pnl().get(i);
                break;
            }
        }
        return latest.subtract(base).setScale(2, RoundingMode.HALF_UP);
    }

    /** 月度收益：月末累计收益 - 上月末（或区间起点）累计收益 */
    private List<ProfitCurveVO.MonthlyPnl> monthlyOf(List<String> dates, List<BigDecimal> pnl) {
        List<ProfitCurveVO.MonthlyPnl> monthly = new ArrayList<>();
        String currentMonth = null;
        BigDecimal base = BigDecimal.ZERO;
        for (int i = 0; i < dates.size(); i++) {
            String month = dates.get(i).substring(0, 7);
            if (currentMonth == null) {
                currentMonth = month;
                base = pnl.get(i);
            } else if (!month.equals(currentMonth)) {
                monthly.add(new ProfitCurveVO.MonthlyPnl(currentMonth, pnl.get(i - 1).subtract(base)));
                currentMonth = month;
                base = pnl.get(i);
            }
        }
        if (currentMonth != null) {
            monthly.add(new ProfitCurveVO.MonthlyPnl(currentMonth, pnl.get(pnl.size() - 1).subtract(base)));
        }
        return monthly;
    }

    /** 沪深300 同区间涨跌幅（%）：Redis 缓存 6h；拉取失败返回全 null 序列（前端断线展示） */
    private List<BigDecimal> benchmarkPct(List<String> dates, LocalDate start) {
        TreeMap<LocalDate, BigDecimal> closes = benchmarkCloses(start.minusDays(FILL_LOOKBACK_DAYS));
        List<BigDecimal> pct = new ArrayList<>(dates.size());
        if (closes.isEmpty()) {
            for (int i = 0; i < dates.size(); i++) {
                pct.add(null);
            }
            return pct;
        }
        BigDecimal first = null;
        for (String date : dates) {
            Map.Entry<LocalDate, BigDecimal> entry = closes.floorEntry(LocalDate.parse(date));
            BigDecimal close = entry == null ? null : entry.getValue();
            if (close != null && first == null) {
                first = close;
            }
            pct.add(close == null || first == null || first.compareTo(BigDecimal.ZERO) <= 0 ? null
                    : close.subtract(first).multiply(BigDecimal.valueOf(100))
                            .divide(first, 2, RoundingMode.HALF_UP));
        }
        return pct;
    }

    /** 沪深300 日 K（缓存优先）；东财 push2his 对指数 secid 同样适用 */
    private TreeMap<LocalDate, BigDecimal> benchmarkCloses(LocalDate from) {
        String cached = redisTemplate.opsForValue().get(BENCH_CACHE_KEY);
        if (cached != null) {
            return toCloseMap(JsonUtils.mapper().readValue(cached, new TypeReference<List<BenchPoint>>() {
            }));
        }
        // 已知数据源处于封堵窗口：直接返回空基准，别让每次打开仪表盘都白等一次拉取
        if ("1".equals(redisTemplate.opsForValue().get(BENCH_DEGRADED_KEY))) {
            LOGGER.debug("基准处于降级窗口，本轮跳过拉取");
            return new TreeMap<>();
        }
        List<EastmoneyClient.KlineItem> items;
        try {
            // ⚠️ 请求路径必须用**快速通道**（只试主域 + 1 个备用域、不重试、域名间不停顿）：
            // 带域名退避的 fetchEtfKline 在数据源封堵时要 60-75 秒才失败，
            // 会把 /dashboard/profit/curve 直接拖超时（实测 76 秒），
            // 且失败不写缓存 → 每次打开页面都要重跑一遍
            items = eastmoneyClient.fetchEtfKlineFast(BENCH_MARKET, BENCH_CODE, from, LocalDate.now());
        } catch (Exception e) {
            redisTemplate.opsForValue().set(BENCH_DEGRADED_KEY, "1", BENCH_DEGRADED_TTL);
            LOGGER.error("沪深300基准行情拉取失败（10 分钟内不再重试），曲线将不含基准线", e);
            return new TreeMap<>();
        }
        if (items.isEmpty()) {
            redisTemplate.opsForValue().set(BENCH_DEGRADED_KEY, "1", BENCH_DEGRADED_TTL);
            return new TreeMap<>();
        }
        // 成功即清掉降级标记，恢复正常的缓存刷新
        redisTemplate.delete(BENCH_DEGRADED_KEY);
        List<BenchPoint> points = items.stream()
                .map(item -> new BenchPoint(item.date().toString(), item.close())).toList();
        redisTemplate.opsForValue().set(BENCH_CACHE_KEY, JsonUtils.toJson(points), BENCH_CACHE_TTL);
        return toCloseMap(points);
    }

    private TreeMap<LocalDate, BigDecimal> toCloseMap(List<BenchPoint> points) {
        TreeMap<LocalDate, BigDecimal> map = new TreeMap<>();
        if (points != null) {
            points.forEach(point -> map.put(LocalDate.parse(point.date()), point.close()));
        }
        return map;
    }

    /** 基金日期→价格映射：ETF 前复权收盘价 / 场外单位净值 */
    private TreeMap<LocalDate, BigDecimal> priceMapOf(FundBasic fund, LocalDate from) {
        TreeMap<LocalDate, BigDecimal> map = new TreeMap<>();
        if (FundTypeEnum.ETF.getCode() == fund.getFundType()) {
            klineMapper.selectList(new LambdaQueryWrapper<FundEtfKline>()
                            .eq(FundEtfKline::getFundCode, fund.getFundCode())
                            .ge(FundEtfKline::getTradeDate, from))
                    .forEach(row -> map.put(row.getTradeDate(), row.getClose()));
        } else {
            navMapper.selectList(new LambdaQueryWrapper<FundNav>()
                            .eq(FundNav::getFundCode, fund.getFundCode())
                            .ge(FundNav::getNavDate, from))
                    .forEach(row -> map.put(row.getNavDate(), row.getUnitNav()));
        }
        return map;
    }

    /** 某基金在目标日（含）之前最近一个交易日的价格，用于 7 日涨跌对照 */
    private BigDecimal priceOnOrBefore(FundBasic fund, LocalDate target) {
        if (FundTypeEnum.ETF.getCode() == fund.getFundType()) {
            FundEtfKline row = klineMapper.selectOne(new LambdaQueryWrapper<FundEtfKline>()
                    .eq(FundEtfKline::getFundCode, fund.getFundCode())
                    .le(FundEtfKline::getTradeDate, target)
                    .orderByDesc(FundEtfKline::getTradeDate).last("limit 1"));
            return row == null ? null : row.getClose();
        }
        FundNav row = navMapper.selectOne(new LambdaQueryWrapper<FundNav>()
                .eq(FundNav::getFundCode, fund.getFundCode())
                .le(FundNav::getNavDate, target)
                .orderByDesc(FundNav::getNavDate).last("limit 1"));
        return row == null ? null : row.getUnitNav();
    }

    /** 有交易流水的基金（含已清仓但仍在自选池的），净投入与曲线都应覆盖；划转流水(fund_code 为空)不参与 */
    private List<FundBasic> tradedFunds() {
        List<String> tradedCodes = tradeFlowMapper.selectList(new LambdaQueryWrapper<TradeFlow>()
                        .select(TradeFlow::getFundCode)
                        .isNotNull(TradeFlow::getFundCode)
                        .groupBy(TradeFlow::getFundCode))
                .stream().map(TradeFlow::getFundCode).toList();
        if (tradedCodes.isEmpty()) {
            return List.of();
        }
        return fundBasicMapper.selectList(new LambdaQueryWrapper<FundBasic>()
                .eq(FundBasic::getStatus, 1).in(FundBasic::getFundCode, tradedCodes));
    }

    /** 净投入 = 累计买入(含费) - 累计卖出(净额) - 累计分红(净额) */

    /** 区间代码 → 起始日；ALL 用极早日期，实际会被首笔流水日期抬升 */
    private LocalDate rangeStart(String range) {
        return switch (range == null ? "" : range) {
            case "1M" -> LocalDate.now().minusDays(30);
            case "3M" -> LocalDate.now().minusDays(90);
            case "6M" -> LocalDate.now().minusDays(180);
            case "YTD" -> LocalDate.of(LocalDate.now().getYear(), 1, 1);
            case "1Y" -> LocalDate.now().minusDays(365);
            case "3Y" -> LocalDate.now().minusDays(1095);
            case "ALL" -> LocalDate.of(2000, 1, 1);
            default -> throw new BizException("非法区间代码: " + range + "（支持 1M/3M/6M/YTD/1Y/3Y/ALL）");
        };
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * 单基金逐日重建的滚动状态：份额与净投入沿流水指针累加（买入增份额加投入，卖出/分红减份额加回款）。
     */
    private static final class FundState {

        /** 日期→价格（前复权收盘/单位净值），含流水种子价 */
        private final TreeMap<LocalDate, BigDecimal> prices;

        /** 按日期升序的全部流水 */
        private final List<TradeFlow> flows;

        /** 已处理到的流水下标 */
        private int flowIndex;

        /** 当前持有份额 */
        private BigDecimal share = BigDecimal.ZERO;

        /** 当前净投入（买入含费为正，卖出/分红净额为负） */
        private BigDecimal netInvested = BigDecimal.ZERO;

        private FundState(TreeMap<LocalDate, BigDecimal> prices, List<TradeFlow> flows) {
            this.prices = prices;
            this.flows = flows;
        }

        /** 将流水推进到指定日期（含当日），累积份额与净投入 */
        private void applyFlowsUpTo(LocalDate date) {
            while (flowIndex < flows.size() && !flows.get(flowIndex).getTradeDate().isAfter(date)) {
                TradeFlow flow = flows.get(flowIndex);
                BigDecimal amount = flow.getAmount() == null ? BigDecimal.ZERO : flow.getAmount();
                BigDecimal fee = flow.getFee() == null ? BigDecimal.ZERO : flow.getFee();
                switch (TradeTypeEnum.of(flow.getTradeType())) {
                    case BUY -> {
                        share = share.add(flow.getShare() == null ? BigDecimal.ZERO : flow.getShare());
                        netInvested = netInvested.add(amount).add(fee);
                    }
                    case SELL -> {
                        share = share.subtract(flow.getShare() == null ? BigDecimal.ZERO : flow.getShare());
                        netInvested = netInvested.subtract(amount).add(fee);
                    }
                    case DIVIDEND -> netInvested = netInvested.subtract(amount).add(fee);
                    default -> {
                        // 不会发生
                    }
                }
                flowIndex++;
            }
        }
    }

    /** 内部计算结果：日期轴 + 逐日累计收益（元）+ 逐日持仓市值（元，区间收益率的基准） */
    private record SeriesData(List<String> dates, List<BigDecimal> pnl, List<BigDecimal> marketValues) {
    }

    /** 区间收益结果：收益金额与收益率（收益率在无基准市值时为 null） */
    private record PeriodPnl(BigDecimal pnl, BigDecimal pct) {
    }

    /** 基准缓存点（JSON 序列化用） */
    private record BenchPoint(String date, BigDecimal close) {
    }
}
