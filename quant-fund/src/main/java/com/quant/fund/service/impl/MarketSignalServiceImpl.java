package com.quant.fund.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.quant.common.exception.BizException;
import com.quant.fund.dto.MarketSignalVO;
import com.quant.fund.entity.FundBasic;
import com.quant.fund.entity.FundEtfKline;
import com.quant.fund.entity.FundNav;
import com.quant.fund.entity.IndexValuation;
import com.quant.fund.mapper.FundBasicMapper;
import com.quant.fund.mapper.FundEtfKlineMapper;
import com.quant.fund.mapper.FundNavMapper;
import com.quant.fund.mapper.IndexValuationMapper;
import com.quant.fund.service.DividendYieldService;
import com.quant.fund.service.FundQueryService;
import com.quant.fund.service.FundTagService;
import com.quant.fund.service.MarketSignalService;
import org.springframework.stereotype.Service;

/**
 * 市场信号实现（V5.39）。
 *
 * <p>取数策略（性能）：自选池一条、ETF 收盘与场外复权净值**各一条批量 SQL**（只取 代码/日期/价格 三列，
 * 全历史一次进内存），几只基金也要几毫秒到几十毫秒，避免 N+1；PE 分位**复用**
 * {@link FundQueryService#percentileOfIndexPe}（与基金池列、估值页签完全同口径），按跟踪指数去重后逐指数计算
 * （同指数的基金共享一次计算）；股息率 TTM 用 {@link DividendYieldService#currentTtmYields} 批量。
 *
 * <p>价格口径与「基金对比」页一致：ETF = 前复权收盘价；场外 = 复权净值（缺失时回退单位净值）。
 * 涨跌幅为正百分数；回撤深度 = 相对历史最高价的跌幅%（≥0）；回撤分位 = 历史每日回撤序列中
 * 小于当前回撤的样本占比（0=处于高点附近，越大越极端）。
 */
@Service
public class MarketSignalServiceImpl implements MarketSignalService {

    /** 涨跌幅窗口（交易日数）——与前端列一一对应 */
    private static final int[] CHG_WINDOWS = {5, 10, 20, 30, 60, 90, 120, 250};

    /** 均线窗口（交易日数） */
    private static final int[] MA_WINDOWS = {20, 60, 200};

    /** "全历史" PE 分位用的窗口年数（覆盖任何基金的历史长度） */
    private static final int PE_WINDOW_ALL_YEARS = 100;

    /** 分位计算的最小样本数：低于此值分位不稳定，返回 null 让前端显示「—」 */
    private static final int MIN_PE_SAMPLES = 30;

    private final FundBasicMapper fundBasicMapper;

    private final FundEtfKlineMapper klineMapper;

    private final FundNavMapper navMapper;

    private final IndexValuationMapper valuationMapper;

    /** PE 分位复用既有实现（口径与基金池列/估值页签一致） */
    private final FundQueryService fundQueryService;

    private final DividendYieldService dividendYieldService;

    private final FundTagService fundTagService;

    public MarketSignalServiceImpl(FundBasicMapper fundBasicMapper, FundEtfKlineMapper klineMapper,
                                   FundNavMapper navMapper, IndexValuationMapper valuationMapper,
                                   FundQueryService fundQueryService, DividendYieldService dividendYieldService,
                                   FundTagService fundTagService) {
        this.fundBasicMapper = fundBasicMapper;
        this.klineMapper = klineMapper;
        this.navMapper = navMapper;
        this.valuationMapper = valuationMapper;
        this.fundQueryService = fundQueryService;
        this.dividendYieldService = dividendYieldService;
        this.fundTagService = fundTagService;
    }

    @Override
    public MarketSignalVO overview(String peWindow) {
        int windowYears = windowYearsOf(peWindow);
        List<FundBasic> funds = fundBasicMapper.selectList(new LambdaQueryWrapper<FundBasic>()
                .eq(FundBasic::getStatus, 1).orderByAsc(FundBasic::getFundCode));
        if (funds.isEmpty()) {
            return new MarketSignalVO(peWindow, List.of());
        }

        // 价格序列：ETF 与场外各一条批量 SQL，fundCode → (日期升序的收盘序列)
        List<String> etfCodes = codesOfType(funds, 1);
        List<String> otcCodes = codesOfType(funds, 2);
        Map<String, List<BigDecimal>> closes = new HashMap<>();
        Map<String, LocalDate> lastDates = new HashMap<>();
        loadEtfCloses(etfCodes, closes, lastDates);
        loadOtcCloses(otcCodes, closes, lastDates);

        // 股息率 TTM（批量）与标签（一次）
        Map<String, BigDecimal> ttmYields = dividendYieldService.currentTtmYields(funds);
        Map<String, List<String>> tags = fundTagService.allFundTags();

        // PE 分位/最新值/样本数：按跟踪指数去重（同指数基金共享计算结果）
        Set<String> indexCodes = new TreeSet<>();
        for (FundBasic fund : funds) {
            if (fund.getIndexCode() != null) {
                indexCodes.add(fund.getIndexCode());
            }
        }
        Map<String, BigDecimal> pePctiles = new HashMap<>();
        Map<String, BigDecimal> latestPes = new HashMap<>();
        Map<String, LocalDate> peDates = new HashMap<>();
        Map<String, Integer> peSamples = new HashMap<>();
        for (String indexCode : indexCodes) {
            Integer samples = peSampleCount(indexCode, windowYears);
            peSamples.put(indexCode, samples);
            // 样本不足时分位不可信（如次新指数），直接不展示而不是给一个误导值
            pePctiles.put(indexCode, samples == null ? null
                    : fundQueryService.percentileOfIndexPe(indexCode, windowYears));
            IndexValuation latest = latestPeOf(indexCode);
            if (latest != null) {
                latestPes.put(indexCode, latest.getPe());
                peDates.put(indexCode, latest.getTradeDate());
            }
        }

        List<MarketSignalVO.Row> rows = new ArrayList<>(funds.size());
        for (FundBasic fund : funds) {
            List<BigDecimal> series = closes.getOrDefault(fund.getFundCode(), List.of());
            BigDecimal[] chgs = new BigDecimal[CHG_WINDOWS.length];
            for (int i = 0; i < CHG_WINDOWS.length; i++) {
                chgs[i] = chgPct(series, CHG_WINDOWS[i]);
            }
            BigDecimal[] mas = new BigDecimal[MA_WINDOWS.length];
            for (int i = 0; i < MA_WINDOWS.length; i++) {
                mas[i] = maOf(series, MA_WINDOWS[i]);
            }
            DrawdownStat dd = drawdownStat(series);

            rows.add(new MarketSignalVO.Row(
                    fund.getFundCode(), fund.getFundName(), fund.getFundType(),
                    tags.getOrDefault(fund.getFundCode(), List.of()),
                    series.isEmpty() ? null : series.get(series.size() - 1),
                    lastDates.get(fund.getFundCode()),
                    chgs[0], chgs[1], chgs[2], chgs[3], chgs[4], chgs[5], chgs[6], chgs[7],
                    aboveMa(series, mas[0]), aboveMa(series, mas[1]), aboveMa(series, mas[2]),
                    maSummary(series, mas),
                    dd.drawdownPct(), dd.drawdownPctile(),
                    fund.getIndexName(),
                    latestPes.get(fund.getIndexCode()),
                    pePctiles.get(fund.getIndexCode()),
                    peDates.get(fund.getIndexCode()),
                    peSamples.get(fund.getIndexCode()),
                    ttmYields.get(fund.getFundCode()),
                    fund.getPremiumRate(), fund.getPremiumDate()));
        }
        return new MarketSignalVO(peWindow, rows);
    }

    /** peWindow 参数 → 年数；非法值直接中文报错（不静默回退，避免"看起来对其实错窗口"） */
    private int windowYearsOf(String peWindow) {
        return switch (peWindow == null ? "" : peWindow) {
            case "3y" -> 3;
            case "5y" -> 5;
            case "10y" -> 10;
            case "all" -> PE_WINDOW_ALL_YEARS;
            default -> throw new BizException("peWindow 仅支持 3y/5y/10y/all");
        };
    }

    private List<String> codesOfType(List<FundBasic> funds, int type) {
        List<String> codes = new ArrayList<>();
        for (FundBasic fund : funds) {
            if (fund.getFundType() != null && fund.getFundType() == type) {
                codes.add(fund.getFundCode());
            }
        }
        return codes;
    }

    /** ETF 前复权收盘：一条 SQL 取全部基金（只取三列，控制传输量） */
    private void loadEtfCloses(List<String> codes, Map<String, List<BigDecimal>> closes, Map<String, LocalDate> dates) {
        if (codes.isEmpty()) {
            return;
        }
        List<FundEtfKline> rows = klineMapper.selectList(new LambdaQueryWrapper<FundEtfKline>()
                .in(FundEtfKline::getFundCode, codes)
                .isNotNull(FundEtfKline::getClose)
                .select(FundEtfKline::getFundCode, FundEtfKline::getTradeDate, FundEtfKline::getClose)
                .orderByAsc(FundEtfKline::getFundCode).orderByAsc(FundEtfKline::getTradeDate));
        for (FundEtfKline row : rows) {
            closes.computeIfAbsent(row.getFundCode(), k -> new ArrayList<>()).add(row.getClose());
            dates.put(row.getFundCode(), row.getTradeDate());
        }
    }

    /** 场外复权净值（缺失回退单位净值）：一条 SQL 取全部基金 */
    private void loadOtcCloses(List<String> codes, Map<String, List<BigDecimal>> closes, Map<String, LocalDate> dates) {
        if (codes.isEmpty()) {
            return;
        }
        List<FundNav> rows = navMapper.selectList(new LambdaQueryWrapper<FundNav>()
                .in(FundNav::getFundCode, codes)
                .select(FundNav::getFundCode, FundNav::getNavDate, FundNav::getUnitNav, FundNav::getAdjNav)
                .orderByAsc(FundNav::getFundCode).orderByAsc(FundNav::getNavDate));
        for (FundNav row : rows) {
            BigDecimal value = row.getAdjNav() != null ? row.getAdjNav() : row.getUnitNav();
            if (value == null) {
                continue;
            }
            closes.computeIfAbsent(row.getFundCode(), k -> new ArrayList<>()).add(value);
            dates.put(row.getFundCode(), row.getNavDate());
        }
    }

    /** 近 n 个交易日涨跌幅%（序列不足返回 null；基数为 0 防除零） */
    private BigDecimal chgPct(List<BigDecimal> series, int n) {
        int i = series.size() - 1;
        int j = i - n;
        if (j < 0) {
            return null;
        }
        BigDecimal base = series.get(j);
        if (base.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return series.get(i).subtract(base).multiply(BigDecimal.valueOf(100))
                .divide(base, 2, RoundingMode.HALF_UP);
    }

    /** n 日均线（含当日；样本不足返回 null） */
    private BigDecimal maOf(List<BigDecimal> series, int n) {
        int size = series.size();
        if (size < n) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = size - n; i < size; i++) {
            sum = sum.add(series.get(i));
        }
        return sum.divide(BigDecimal.valueOf(n), 6, RoundingMode.HALF_UP);
    }

    /** 现价相对均线位置（1=上 0=下；均线缺失为 null） */
    private Integer aboveMa(List<BigDecimal> series, BigDecimal ma) {
        if (ma == null || series.isEmpty()) {
            return null;
        }
        return series.get(series.size() - 1).compareTo(ma) > 0 ? 1 : 0;
    }

    /** 趋势汇总：三线全上=多头排列、全下=空头排列、其余=震荡；任一均线缺失为 null */
    private String maSummary(List<BigDecimal> series, BigDecimal[] mas) {
        Integer a = aboveMa(series, mas[0]);
        Integer b = aboveMa(series, mas[1]);
        Integer c = aboveMa(series, mas[2]);
        if (a == null || b == null || c == null) {
            return null;
        }
        if (a == 1 && b == 1 && c == 1) {
            return "多头排列";
        }
        if (a == 0 && b == 0 && c == 0) {
            return "空头排列";
        }
        return "震荡";
    }

    /**
     * 回撤统计：一次遍历同时得 当前回撤深度% 与 回撤分位。
     * 分位定义：历史每日回撤中小于当前回撤的样本占比（含当日；越大越极端）。
     */
    private DrawdownStat drawdownStat(List<BigDecimal> series) {
        int n = series.size();
        if (n == 0) {
            return new DrawdownStat(null, null);
        }
        double[] dds = new double[n];
        double runMax = 0;
        int below = 0;
        for (int i = 0; i < n; i++) {
            double price = series.get(i).doubleValue();
            if (price > runMax) {
                runMax = price;
            }
            double dd = runMax > 0 ? (runMax - price) / runMax * 100 : 0;
            dds[i] = dd;
        }
        double current = dds[n - 1];
        for (double dd : dds) {
            if (dd < current) {
                below++;
            }
        }
        return new DrawdownStat(
                BigDecimal.valueOf(current).setScale(2, RoundingMode.HALF_UP),
                BigDecimal.valueOf(below * 100.0 / n).setScale(1, RoundingMode.HALF_UP));
    }

    /** 回撤统计载体（当前深度 + 历史分位） */
    private record DrawdownStat(BigDecimal drawdownPct, BigDecimal drawdownPctile) {
    }

    /** 某指数最新一条 PE（取值与日期一起展示） */
    private IndexValuation latestPeOf(String indexCode) {
        List<IndexValuation> rows = valuationMapper.selectList(new LambdaQueryWrapper<IndexValuation>()
                .eq(IndexValuation::getIndexCode, indexCode)
                .isNotNull(IndexValuation::getPe)
                .orderByDesc(IndexValuation::getTradeDate)
                .last("limit 1"));
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 分位窗口内的 PE 样本天数（判断分位可信度；不足 MIN_PE_SAMPLES 时分位本就为 null） */
    private Integer peSampleCount(String indexCode, int windowYears) {
        long count = valuationMapper.selectCount(new LambdaQueryWrapper<IndexValuation>()
                .eq(IndexValuation::getIndexCode, indexCode)
                .ge(IndexValuation::getTradeDate, LocalDate.now().minusYears(windowYears))
                .isNotNull(IndexValuation::getPe));
        if (count < MIN_PE_SAMPLES) {
            return null;
        }
        return (int) count;
    }
}
