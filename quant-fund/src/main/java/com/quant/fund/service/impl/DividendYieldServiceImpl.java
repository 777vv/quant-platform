package com.quant.fund.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.quant.fund.dto.DividendYieldVO;
import com.quant.fund.entity.FundBasic;
import com.quant.fund.entity.FundDividend;
import com.quant.fund.entity.FundEtfKline;
import com.quant.fund.entity.FundNav;
import com.quant.fund.enums.FundTypeEnum;
import com.quant.fund.mapper.FundBasicMapper;
import com.quant.fund.mapper.FundDividendMapper;
import com.quant.fund.mapper.FundEtfKlineMapper;
import com.quant.fund.mapper.FundNavMapper;
import com.quant.common.exception.BizException;
import com.quant.fund.service.DividendYieldService;
import org.springframework.stereotype.Service;

/**
 * 分红股息率实现。
 *
 * <p>TTM 阶跃点的构造：股息率只在"某笔分红进入 12 个月窗口"（除息日）和"滚出窗口"（除息日 + 1 年）时变化，
 * 因此只需在这两类日子（外加区间首末）各取一个点，曲线用阶梯连线即可——数据量极小，
 * 既不影响主图加载，也不用逐日算。
 */
@Service
public class DividendYieldServiceImpl implements DividendYieldService {

    /** TTM 窗口（月） */
    private static final int TTM_MONTHS = 12;

    /** 每 10 份派现 → 每份分红的除数 */
    private static final BigDecimal PER_TEN = BigDecimal.TEN;

    /** 百分比基数 */
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /** 价格缺失时的说明文案 */
    private static final String HINT_PRICE_MISSING =
            "历史股息率需要未复权价（前复权价会把历史价格压低、高估股息率）；当前该基金缺少未复权价，"
                    + "下一次数据同步成功后会补上";

    private final FundDividendMapper dividendMapper;

    private final FundEtfKlineMapper klineMapper;

    private final FundNavMapper navMapper;

    private final FundBasicMapper fundBasicMapper;

    public DividendYieldServiceImpl(FundDividendMapper dividendMapper, FundEtfKlineMapper klineMapper,
                                    FundNavMapper navMapper, FundBasicMapper fundBasicMapper) {
        this.dividendMapper = dividendMapper;
        this.klineMapper = klineMapper;
        this.navMapper = navMapper;
        this.fundBasicMapper = fundBasicMapper;
    }

    @Override
    public DividendYieldVO series(String fundCode, int rangeDays) {
        // 直接查基金行（不注入 FundQueryService：它反过来依赖本服务，会形成循环依赖）
        FundBasic fund = fundBasicMapper.selectOne(new LambdaQueryWrapper<FundBasic>()
                .eq(FundBasic::getFundCode, fundCode));
        if (fund == null) {
            throw new BizException("基金不存在: " + fundCode);
        }
        List<FundDividend> dividends = dividendsOf(List.of(fundCode)).getOrDefault(fundCode, List.of());
        LocalDate start = LocalDate.now().minusDays(Math.max(rangeDays, 1));
        // 预热一年：区间首日就要能算出"过去 12 个月"的分红合计
        TreeMap<LocalDate, BigDecimal> prices = realPrices(fund, start.minusMonths(TTM_MONTHS).minusDays(7));
        boolean priceAvailable = !prices.isEmpty();
        List<DividendYieldVO.EventYield> events = new ArrayList<>();
        for (FundDividend dividend : dividends) {
            if (dividend.getExDate() == null || dividend.getExDate().isBefore(start)) {
                continue;
            }
            BigDecimal perShare = perShare(dividend);
            BigDecimal price = priceOn(prices, dividend.getExDate());
            events.add(new DividendYieldVO.EventYield(dividend.getExDate().toString(), perShare, price,
                    yieldPct(perShare, price)));
        }
        List<DividendYieldVO.TtmPoint> ttm = ttmPoints(dividends, prices, start);
        BigDecimal latest = ttm.isEmpty() ? null : ttm.get(ttm.size() - 1).yieldPct();
        return new DividendYieldVO(fund.getFundCode(), fund.getFundName(), events, ttm, priceAvailable, latest,
                priceAvailable ? null : HINT_PRICE_MISSING);
    }

    @Override
    public Map<String, BigDecimal> currentTtmYields(List<FundBasic> funds) {
        if (funds.isEmpty()) {
            return Map.of();
        }
        List<String> codes = funds.stream().map(FundBasic::getFundCode).toList();
        Map<String, List<FundDividend>> byFund = dividendsOf(codes);
        LocalDate windowStart = LocalDate.now().minusMonths(TTM_MONTHS);
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (FundBasic fund : funds) {
            BigDecimal sum = BigDecimal.ZERO;
            for (FundDividend dividend : byFund.getOrDefault(fund.getFundCode(), List.of())) {
                if (dividend.getExDate() != null && !dividend.getExDate().isBefore(windowStart)) {
                    sum = sum.add(perShare(dividend));
                }
            }
            if (sum.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal price = latestRealPrice(fund);
            BigDecimal pct = yieldPct(sum, price);
            if (pct != null) {
                result.put(fund.getFundCode(), pct);
            }
        }
        return result;
    }

    /**
     * TTM 阶跃点：除息日（进入窗口）与除息日 + 1 年（滚出窗口）两类日期，外加区间首末。
     *
     * @param dividends 全部分红（含区间之前的，用于算区间首日的"过去 12 个月"）
     * @param prices    真实价格序列（可为空）
     * @param start     区间起点
     */
    private List<DividendYieldVO.TtmPoint> ttmPoints(List<FundDividend> dividends,
                                                     TreeMap<LocalDate, BigDecimal> prices, LocalDate start) {
        TreeSet<LocalDate> points = new TreeSet<>();
        for (FundDividend dividend : dividends) {
            if (dividend.getExDate() == null) {
                continue;
            }
            points.add(dividend.getExDate());
            points.add(dividend.getExDate().plusMonths(TTM_MONTHS));
        }
        if (!prices.isEmpty()) {
            points.add(prices.lastKey());
        }
        points.add(start);
        List<DividendYieldVO.TtmPoint> list = new ArrayList<>();
        for (LocalDate point : points) {
            if (point.isBefore(start)) {
                continue;
            }
            BigDecimal sum = BigDecimal.ZERO;
            LocalDate windowStart = point.minusMonths(TTM_MONTHS);
            for (FundDividend dividend : dividends) {
                if (dividend.getExDate() != null && !dividend.getExDate().isBefore(windowStart)
                        && !dividend.getExDate().isAfter(point)) {
                    sum = sum.add(perShare(dividend));
                }
            }
            list.add(new DividendYieldVO.TtmPoint(point.toString(),
                    yieldPct(sum, priceOn(prices, point))));
        }
        return list;
    }

    /** 按代码批量取分红记录（一次查询，避免列表逐只查） */
    private Map<String, List<FundDividend>> dividendsOf(List<String> fundCodes) {
        Map<String, List<FundDividend>> result = new HashMap<>();
        if (fundCodes.isEmpty()) {
            return result;
        }
        dividendMapper.selectList(new LambdaQueryWrapper<FundDividend>()
                        .in(FundDividend::getFundCode, fundCodes)
                        .orderByAsc(FundDividend::getExDate))
                .forEach(dividend -> result.computeIfAbsent(dividend.getFundCode(), key -> new ArrayList<>())
                        .add(dividend));
        return result;
    }

    /** 每份分红（元）= 每 10 份派现 ÷ 10；无金额按 0 */
    private BigDecimal perShare(FundDividend dividend) {
        if (dividend.getPer10Amount() == null) {
            return BigDecimal.ZERO;
        }
        return dividend.getPer10Amount().divide(PER_TEN, 6, RoundingMode.HALF_UP);
    }

    /** 股息率（%）= 每份分红 ÷ 真实价格 × 100；分子为 0 或价格缺失/非正时返回 null */
    private BigDecimal yieldPct(BigDecimal perShareSum, BigDecimal price) {
        if (perShareSum == null || perShareSum.compareTo(BigDecimal.ZERO) <= 0
                || price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return perShareSum.multiply(HUNDRED).divide(price, 2, RoundingMode.HALF_UP);
    }

    /** 真实价格：取目标日（含）之前最近一个有价的交易日 */
    private BigDecimal priceOn(TreeMap<LocalDate, BigDecimal> prices, LocalDate date) {
        if (prices.isEmpty() || date == null) {
            return null;
        }
        Map.Entry<LocalDate, BigDecimal> entry = prices.floorEntry(date);
        return entry == null ? null : entry.getValue();
    }

    /** 最新真实价格：ETF 取未复权收盘（缺失时退到前复权收盘——最新一日两者相等），场外取单位净值 */
    private BigDecimal latestRealPrice(FundBasic fund) {
        if (FundTypeEnum.ETF.getCode() == fund.getFundType()) {
            List<FundEtfKline> rows = klineMapper.selectList(new LambdaQueryWrapper<FundEtfKline>()
                    .eq(FundEtfKline::getFundCode, fund.getFundCode())
                    .orderByDesc(FundEtfKline::getTradeDate)
                    .last("limit 1"));
            if (rows.isEmpty()) {
                return null;
            }
            FundEtfKline row = rows.get(0);
            return row.getUnadjClose() != null ? row.getUnadjClose() : row.getClose();
        }
        List<FundNav> rows = navMapper.selectList(new LambdaQueryWrapper<FundNav>()
                .eq(FundNav::getFundCode, fund.getFundCode())
                .orderByDesc(FundNav::getNavDate)
                .last("limit 1"));
        return rows.isEmpty() ? null : rows.get(0).getUnitNav();
    }

    /**
     * 区间内可用的真实价格序列。
     * ETF → `unadj_close`（缺失的行跳过，全部缺失即视为不可用）；场外 → `unit_nav`。
     */
    private TreeMap<LocalDate, BigDecimal> realPrices(FundBasic fund, LocalDate from) {
        TreeMap<LocalDate, BigDecimal> map = new TreeMap<>();
        if (FundTypeEnum.ETF.getCode() == fund.getFundType()) {
            klineMapper.selectList(new LambdaQueryWrapper<FundEtfKline>()
                            .eq(FundEtfKline::getFundCode, fund.getFundCode())
                            .ge(FundEtfKline::getTradeDate, from)
                            .isNotNull(FundEtfKline::getUnadjClose))
                    .forEach(row -> map.put(row.getTradeDate(), row.getUnadjClose()));
        } else {
            navMapper.selectList(new LambdaQueryWrapper<FundNav>()
                            .eq(FundNav::getFundCode, fund.getFundCode())
                            .ge(FundNav::getNavDate, from)
                            .isNotNull(FundNav::getUnitNav))
                    .forEach(row -> map.put(row.getNavDate(), row.getUnitNav()));
        }
        return map;
    }
}
