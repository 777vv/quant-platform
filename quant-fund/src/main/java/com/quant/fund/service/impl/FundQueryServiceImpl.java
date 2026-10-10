package com.quant.fund.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.quant.common.exception.BizException;
import com.quant.common.result.PageResult;
import com.quant.fund.dto.FundDetailVO;
import com.quant.fund.dto.LastQuote;
import com.quant.fund.dto.SeriesPoint;
import com.quant.fund.dto.ValuationSeriesVO;
import com.quant.fund.dto.WatchItemVO;
import com.quant.fund.entity.FundBasic;
import com.quant.fund.entity.FundEtfKline;
import com.quant.fund.entity.FundNav;
import com.quant.fund.entity.FundPosition;
import com.quant.fund.entity.IndexValuation;
import com.quant.fund.enums.FundTypeEnum;
import com.quant.fund.mapper.FundBasicMapper;
import com.quant.fund.mapper.FundEtfKlineMapper;
import com.quant.fund.mapper.FundNavMapper;
import com.quant.fund.mapper.FundPositionMapper;
import com.quant.fund.mapper.IndexValuationMapper;
import com.quant.fund.service.FundQueryService;
import com.quant.fund.service.FundScaleHistoryService;
import com.quant.fund.service.DividendYieldService;
import com.quant.fund.service.FundTagService;
import org.springframework.stereotype.Service;

/**
 * 基金查询服务实现
 */
@Service
public class FundQueryServiceImpl implements FundQueryService {

    private static final int BATCH_REASONABLE_MAX = 5000;

    /** 分页每页上限（同时用于 LIMIT 拼接的安全钳制） */
    private static final long MAX_PAGE_SIZE = 200;

    private final FundBasicMapper fundBasicMapper;

    private final FundEtfKlineMapper klineMapper;

    private final FundNavMapper navMapper;

    private final IndexValuationMapper valuationMapper;

    private final FundPositionMapper positionMapper;

    private final FundTagService fundTagService;

    /** 分红股息率（列表列的 TTM 口径；批量计算避免逐只查询） */
    private final DividendYieldService dividendYieldService;

    /** 规模历史（V6.15）：列表/详情展示"每日估算规模"用 */
    private final FundScaleHistoryService scaleHistoryService;

    public FundQueryServiceImpl(FundBasicMapper fundBasicMapper, FundEtfKlineMapper klineMapper,
                                FundNavMapper navMapper, IndexValuationMapper valuationMapper,
                                FundPositionMapper positionMapper, FundTagService fundTagService,
                                DividendYieldService dividendYieldService, FundScaleHistoryService scaleHistoryService) {
        this.fundBasicMapper = fundBasicMapper;
        this.klineMapper = klineMapper;
        this.navMapper = navMapper;
        this.valuationMapper = valuationMapper;
        this.positionMapper = positionMapper;
        this.fundTagService = fundTagService;
        this.dividendYieldService = dividendYieldService;
        this.scaleHistoryService = scaleHistoryService;
    }

    /** 下拉选项缓存（V6.01）：15 分钟过期回源；单用户系统本地缓存即可，重启失效无妨 */
    private static final long OPTIONS_CACHE_MINUTES = 15;
    private volatile List<com.quant.fund.dto.FundOptionVO> optionsCache;
    private volatile java.time.LocalDateTime optionsLoadedAt;

    @Override
    public List<com.quant.fund.dto.FundOptionVO> fundOptions() {
        List<com.quant.fund.dto.FundOptionVO> cached = optionsCache;
        if (cached != null && optionsLoadedAt != null
                && optionsLoadedAt.plusMinutes(OPTIONS_CACHE_MINUTES).isAfter(java.time.LocalDateTime.now())) {
            return cached;
        }
        List<com.quant.fund.dto.FundOptionVO> fresh = fundBasicMapper.selectList(
                        new LambdaQueryWrapper<com.quant.fund.entity.FundBasic>()
                                .eq(com.quant.fund.entity.FundBasic::getStatus, 1)
                                .orderByAsc(com.quant.fund.entity.FundBasic::getFundCode))
                .stream()
                .map(fund -> {
                    com.quant.fund.dto.FundOptionVO vo = new com.quant.fund.dto.FundOptionVO();
                    vo.setFundCode(fund.getFundCode());
                    vo.setFundName(fund.getFundName());
                    vo.setFundType(fund.getFundType());
                    return vo;
                })
                .toList();
        optionsCache = fresh;
        optionsLoadedAt = java.time.LocalDateTime.now();
        return fresh;
    }

    @Override
    public List<WatchItemVO> watchlist() {
        Set<String> holdings = holdingCodes();
        List<FundBasic> funds = fundBasicMapper.selectList(new LambdaQueryWrapper<FundBasic>()
                .eq(FundBasic::getStatus, 1).orderByAsc(FundBasic::getFundCode));
        Map<String, BigDecimal> ttmYields = dividendYieldService.currentTtmYields(funds);
        Map<String, FundScaleHistoryService.EstimatedScale> estimated = scaleHistoryService.latestEstimated(
                funds.stream().map(FundBasic::getFundCode).toList());
        return funds.stream()
                .map(fund -> toWatchItem(fund, holdings, ttmYields, estimated.get(fund.getFundCode())))
                .toList();
    }

    @Override
    public PageResult<WatchItemVO> pageWatchlist(String keyword, String tag, long page, long size) {
        List<String> tagCodes = null;
        if (tag != null && !tag.isBlank()) {
            tagCodes = fundTagService.fundCodesOfTag(tag.trim());
            if (tagCodes.isEmpty()) {
                // 标签下没有基金：返回空页，而不是退化成"忽略标签条件"
                return PageResult.of(0, List.of());
            }
        }
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Set<String> holdings = holdingCodes();
        long total = fundBasicMapper.selectCount(baseWrapper(keyword, tagCodes));
        // 持仓段：持仓基金数量很少，直接一次取出（同样受关键词/标签条件约束），
        // 这样"持仓优先"不依赖数据库方言的布尔排序，翻页也只需拼接两段
        List<FundBasic> held = holdings.isEmpty() ? List.of() : sortHeldByMarketValueDesc(
                fundBasicMapper.selectList(baseWrapper(keyword, tagCodes).in(FundBasic::getFundCode, holdings)));
        List<FundBasic> records = slicePage(keyword, tagCodes, holdings, held, (safePage - 1) * safeSize, safeSize);
        Map<String, BigDecimal> ttmYields = dividendYieldService.currentTtmYields(records);
        // 每日估算规模（V6.15）：本页基金一次批量查（场外基金本来就没有估算行，自然为空）
        Map<String, FundScaleHistoryService.EstimatedScale> estimated = scaleHistoryService.latestEstimated(
                records.stream().map(FundBasic::getFundCode).toList());
        return PageResult.of(total, records.stream()
                .map(fund -> toWatchItem(fund, holdings, ttmYields, estimated.get(fund.getFundCode())))
                .toList());
    }

    /**
     * 持仓段排序（V6.13 用户口径）：**持仓市值（最新价 × 份额）从大到小**，同市值按基金代码升序。
     * 持仓只占少数几只，这里在内存排序（每只一次"最近两根 K 线/净值"查询，与列表渲染同一取数口径）。
     */
    private List<FundBasic> sortHeldByMarketValueDesc(List<FundBasic> held) {
        if (held.size() <= 1) {
            return held;
        }
        Map<String, BigDecimal> shares = new HashMap<>();
        for (FundPosition position : positionMapper.selectList(new LambdaQueryWrapper<FundPosition>()
                .in(FundPosition::getFundCode, held.stream().map(FundBasic::getFundCode).toList()))) {
            shares.put(position.getFundCode(),
                    position.getTotalShare() == null ? BigDecimal.ZERO : position.getTotalShare());
        }
        Map<String, BigDecimal> marketValues = new HashMap<>();
        for (FundBasic fund : held) {
            LastQuote quote = lastQuote(fund);
            BigDecimal share = shares.getOrDefault(fund.getFundCode(), BigDecimal.ZERO);
            marketValues.put(fund.getFundCode(), quote == null || quote.price() == null
                    ? BigDecimal.ZERO : quote.price().multiply(share));
        }
        return held.stream()
                .sorted(Comparator.comparing((FundBasic f) -> marketValues.getOrDefault(f.getFundCode(), BigDecimal.ZERO))
                        .reversed()
                        .thenComparing(FundBasic::getFundCode))
                .toList();
    }

    /** 自选筛选条件（状态正常 + 关键词 + 标签），每次调用返回新实例（MP 的 wrapper 会被就地修改） */
    private LambdaQueryWrapper<FundBasic> baseWrapper(String keyword, List<String> tagCodes) {
        LambdaQueryWrapper<FundBasic> wrapper = new LambdaQueryWrapper<FundBasic>().eq(FundBasic::getStatus, 1);
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(FundBasic::getFundCode, kw).or().like(FundBasic::getFundName, kw));
        }
        if (tagCodes != null) {
            wrapper.in(FundBasic::getFundCode, tagCodes);
        }
        return wrapper;
    }

    /**
     * 取"持仓段 + 其余段"拼接后的第 [offset, offset+size) 条。
     * 持仓段在前（按持仓市值降序，V6.13），其余段在后（按**规模降序**、同规模按代码升序，V6.14），
     * 两段拼接即整体有序。
     *
     * @param held   持仓段（已按持仓市值降序，且已过滤关键词/标签）
     * @param offset 全局偏移量
     * @param size   每页条数（已钳制）
     */
    private List<FundBasic> slicePage(String keyword, List<String> tagCodes, Set<String> holdings,
            List<FundBasic> held, long offset, long size) {
        List<FundBasic> rows = new ArrayList<>();
        int fromHeld = (int) Math.max(0, Math.min(size, held.size() - offset));
        if (fromHeld > 0) {
            rows.addAll(held.subList((int) offset, (int) offset + fromHeld));
        }
        int needOthers = (int) (size - fromHeld);
        if (needOthers > 0) {
            long othersOffset = Math.max(0, offset - held.size());
            // 其余段按偏移量直接取：MP 的分页参数按"整页对齐"计，无法表达任意偏移，故用 LIMIT o, n
            // （offsets 均为已钳制的 long，不存在拼接注入问题；本项目数据库固定为 MySQL）
            rows.addAll(fundBasicMapper.selectList(baseWrapper(keyword, tagCodes)
                    .notIn(!holdings.isEmpty(), FundBasic::getFundCode, holdings)
                    // 规模降序（V6.14 用户口径）：MySQL 的 DESC 会把 NULL 排在最后 ✓；
                    // 同规模（含都为 NULL）再按代码升序，保证分页顺序稳定
                    .orderByDesc(FundBasic::getFundScale)
                    .orderByAsc(FundBasic::getFundCode)
                    .last("LIMIT " + othersOffset + ", " + needOthers)));
        }
        return rows;
    }

    /** 持仓基金代码集合（份额 &gt; 0）：用于列表排序与"持仓"标识 */
    private Set<String> holdingCodes() {
        return positionMapper.selectList(new LambdaQueryWrapper<FundPosition>()
                        .gt(FundPosition::getTotalShare, BigDecimal.ZERO))
                .stream().map(FundPosition::getFundCode).collect(Collectors.toSet());
    }

    /**
     * 自选列表条目组装。
     *
     * @param holdings 持仓基金代码集合（用于持仓标识，避免逐只查询）
     */
    private WatchItemVO toWatchItem(FundBasic fund, Set<String> holdings, Map<String, BigDecimal> ttmYields,
            FundScaleHistoryService.EstimatedScale estimated) {
        LastQuote quote = lastQuote(fund);
        BigDecimal percentile = fund.getIndexCode() == null ? null : percentileOfIndexPe(fund.getIndexCode(), 10);
        return new WatchItemVO(
                fund.getFundCode(), fund.getFundName(), fund.getFundType(),
                FundTypeEnum.ETF.getCode() == fund.getFundType() ? FundTypeEnum.ETF.getDesc() : FundTypeEnum.OTC.getDesc(),
                fund.getIndexName(), fund.getIndexCode(),
                quote == null ? null : quote.price(),
                quote == null ? null : quote.changePct(),
                percentile, fund.getLastSyncDate(), fund.getLastSyncAt(), fund.getInceptionDate(),
                estimated == null ? null : estimated.scale(), estimated == null ? null : estimated.date(),
                fund.getFundScale(), fund.getFundScaleDate(),
                opFeeRateOf(fund), fund.getMgmtFeeRate(), fund.getCustFeeRate(), fund.getSalesFeeRate(),
                fund.getPremiumRate(), fund.getPremiumDate(),
                holdings.contains(fund.getFundCode()),
                ttmYields.get(fund.getFundCode()));
    }

    /** 运作费率合计（%/年）= 管理费 + 托管费 + 销售服务费；三项都为空时返回 null（前端显示 --） */
    private BigDecimal opFeeRateOf(FundBasic fund) {
        if (fund.getMgmtFeeRate() == null && fund.getCustFeeRate() == null && fund.getSalesFeeRate() == null) {
            return null;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal rate : new BigDecimal[] {fund.getMgmtFeeRate(), fund.getCustFeeRate(), fund.getSalesFeeRate()}) {
            if (rate != null) {
                total = total.add(rate);
            }
        }
        return total;
    }

    @Override
    public FundDetailVO detail(String fundCode) {
        FundBasic fund = getByCodeRequired(fundCode);
        LastQuote quote = lastQuote(fund);
        FundScaleHistoryService.EstimatedScale estimated = scaleHistoryService.latestEstimated(fund.getFundCode());
        return new FundDetailVO(
                fund.getFundCode(), fund.getFundName(), fund.getFundType(), typeDesc(fund),
                fund.getMarket(), fund.getIndexCode(), fund.getIndexName(), fund.getInceptionDate(),
                fund.getFundCompany(),
                fund.getFundScale(), fund.getFundScaleDate(),
                estimated == null ? null : estimated.scale(), estimated == null ? null : estimated.date(),
                opFeeRateOf(fund),
                fund.getMgmtFeeRate(), fund.getCustFeeRate(), fund.getSalesFeeRate(),
                fund.getPremiumRate(), fund.getPremiumDate(),
                quote == null ? null : quote.price(),
                quote == null ? null : quote.changePct(),
                quote == null ? null : quote.date(),
                fund.getLastSyncDate());
    }

    @Override
    public List<SeriesPoint> kline(String fundCode, int rangeDays) {
        return kline(fundCode, rangeDays, null, null);
    }

    @Override
    public List<SeriesPoint> kline(String fundCode, int rangeDays, LocalDate start, LocalDate end) {
        getByCodeRequired(fundCode);
        // 自定义区间优先：按 [start, end] 精确过滤（不做额外回看），否则按 rangeDays 回看
        boolean custom = start != null && end != null;
        LambdaQueryWrapper<FundEtfKline> wrapper = new LambdaQueryWrapper<FundEtfKline>()
                .eq(FundEtfKline::getFundCode, fundCode);
        if (custom) {
            wrapper.ge(FundEtfKline::getTradeDate, start).le(FundEtfKline::getTradeDate, end);
        } else {
            wrapper.gt(FundEtfKline::getTradeDate, LocalDate.now().minusDays(rangeDays + 30L));
        }
        List<FundEtfKline> rows = klineMapper.selectList(wrapper
                .orderByDesc(FundEtfKline::getTradeDate)
                .last("limit " + (custom ? BATCH_REASONABLE_MAX : Math.min(rangeDays * 2, BATCH_REASONABLE_MAX))));
        Collections.reverse(rows);
        List<SeriesPoint> points = new ArrayList<>(rows.size());
        for (FundEtfKline row : rows) {
            points.add(SeriesPoint.ofKline(row.getTradeDate(), row.getOpen(), row.getClose(),
                    row.getHigh(), row.getLow(), row.getVolume()));
        }
        return points;
    }

    @Override
    public List<SeriesPoint> nav(String fundCode, int rangeDays) {
        return nav(fundCode, rangeDays, null, null);
    }

    @Override
    public List<SeriesPoint> nav(String fundCode, int rangeDays, LocalDate start, LocalDate end) {
        getByCodeRequired(fundCode);
        boolean custom = start != null && end != null;
        LambdaQueryWrapper<FundNav> wrapper = new LambdaQueryWrapper<FundNav>()
                .eq(FundNav::getFundCode, fundCode);
        if (custom) {
            wrapper.ge(FundNav::getNavDate, start).le(FundNav::getNavDate, end);
        } else {
            wrapper.gt(FundNav::getNavDate, LocalDate.now().minusDays(rangeDays + 30L));
        }
        List<FundNav> rows = navMapper.selectList(wrapper
                .orderByDesc(FundNav::getNavDate)
                .last("limit " + (custom ? BATCH_REASONABLE_MAX : Math.min(rangeDays * 2, BATCH_REASONABLE_MAX))));
        Collections.reverse(rows);
        List<SeriesPoint> points = new ArrayList<>(rows.size());
        for (FundNav row : rows) {
            points.add(SeriesPoint.ofNav(row.getNavDate(), row.getUnitNav(), row.getAccNav(), row.getAdjNav()));
        }
        return points;
    }

    @Override
    public ValuationSeriesVO valuation(String fundCode, int rangeDays) {
        FundBasic fund = getByCodeRequired(fundCode);
        if (fund.getIndexCode() == null) {
            return new ValuationSeriesVO(null, null, "PE", List.of(), null, null, false);
        }
        List<IndexValuation> rows = valuationMapper.selectList(new LambdaQueryWrapper<IndexValuation>()
                .eq(IndexValuation::getIndexCode, fund.getIndexCode())
                .gt(IndexValuation::getTradeDate, LocalDate.now().minusDays(rangeDays + 30L))
                .isNotNull(IndexValuation::getPe)
                .orderByAsc(IndexValuation::getTradeDate));
        List<SeriesPoint> series = new ArrayList<>(rows.size());
        for (IndexValuation row : rows) {
            series.add(SeriesPoint.ofPe(row.getTradeDate(), row.getPe()));
        }
        BigDecimal latestPe = series.isEmpty() ? null : series.get(series.size() - 1).pe();
        BigDecimal percentile = percentileOfIndexPe(fund.getIndexCode(), 10);
        return new ValuationSeriesVO(fund.getIndexCode(), fund.getIndexName(), "PE",
                series, latestPe, percentile, !series.isEmpty());
    }

    @Override
    public void removeFromWatchlist(String fundCode) {
        FundBasic fund = getByCodeRequired(fundCode);
        // V2.2：持仓中的基金不允许移出自选（防误删导致持仓/收益统计失去行情来源）；
        // 已清仓但有历史流水的基金仍可移出（历史数据保留，重新导入即恢复）
        FundPosition position = positionMapper.selectOne(new LambdaQueryWrapper<FundPosition>()
                .eq(FundPosition::getFundCode, fundCode));
        if (position != null && position.getTotalShare() != null
                && position.getTotalShare().compareTo(BigDecimal.ZERO) > 0) {
            throw new BizException("该基金当前持仓 " + position.getTotalShare()
                    + " 份，请先在详情页卖出全部份额后再移出自选（历史数据会保留）");
        }
        fund.setStatus(0);
        fundBasicMapper.updateById(fund);
    }

    @Override
    public FundBasic getByCodeRequired(String fundCode) {
        FundBasic fund = fundBasicMapper.selectOne(
                new LambdaQueryWrapper<FundBasic>().eq(FundBasic::getFundCode, fundCode));
        if (fund == null || fund.getStatus() != 1) {
            throw new BizException("基金不在自选池: " + fundCode);
        }
        return fund;
    }

    @Override
    public LastQuote lastQuote(FundBasic fund) {
        if (FundTypeEnum.ETF.getCode() == fund.getFundType()) {
            List<FundEtfKline> rows = klineMapper.selectList(new LambdaQueryWrapper<FundEtfKline>()
                    .eq(FundEtfKline::getFundCode, fund.getFundCode())
                    .orderByDesc(FundEtfKline::getTradeDate)
                    .last("limit 2"));
            if (rows.isEmpty()) {
                return null;
            }
            FundEtfKline last = rows.get(0);
            BigDecimal prev = rows.size() > 1 ? rows.get(1).getClose() : null;
            return new LastQuote(last.getClose(), prev, changePct(last.getClose(), prev), last.getTradeDate());
        }
        List<FundNav> rows = navMapper.selectList(new LambdaQueryWrapper<FundNav>()
                .eq(FundNav::getFundCode, fund.getFundCode())
                .orderByDesc(FundNav::getNavDate)
                .last("limit 2"));
        if (rows.isEmpty()) {
            return null;
        }
        FundNav last = rows.get(0);
        BigDecimal prev = rows.size() > 1 ? rows.get(1).getUnitNav() : null;
        return new LastQuote(last.getUnitNav(), prev, changePct(last.getUnitNav(), prev), last.getNavDate());
    }

    @Override
    public BigDecimal percentileOfIndexPe(String indexCode, int windowYears) {
        List<IndexValuation> rows = valuationMapper.selectList(new LambdaQueryWrapper<IndexValuation>()
                .eq(IndexValuation::getIndexCode, indexCode)
                .ge(IndexValuation::getTradeDate, LocalDate.now().minusYears(windowYears))
                .isNotNull(IndexValuation::getPe)
                .orderByAsc(IndexValuation::getTradeDate));
        if (rows.isEmpty()) {
            return null;
        }
        BigDecimal latest = rows.get(rows.size() - 1).getPe();
        long notGreater = rows.stream().filter(row -> row.getPe().compareTo(latest) <= 0).count();
        return BigDecimal.valueOf(notGreater * 100.0 / rows.size()).setScale(1, RoundingMode.HALF_UP);
    }

    private BigDecimal changePct(BigDecimal price, BigDecimal prev) {
        if (price == null || prev == null || prev.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return price.subtract(prev).multiply(BigDecimal.valueOf(100)).divide(prev, 2, RoundingMode.HALF_UP);
    }

    private String typeDesc(FundBasic fund) {
        return FundTypeEnum.ETF.getCode() == fund.getFundType() ? FundTypeEnum.ETF.getDesc() : FundTypeEnum.OTC.getDesc();
    }
}
