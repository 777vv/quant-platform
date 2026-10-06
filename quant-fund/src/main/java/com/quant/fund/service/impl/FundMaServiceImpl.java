package com.quant.fund.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.quant.common.exception.BizException;
import com.quant.fund.dto.FundMaVO;
import com.quant.fund.dto.MaRunResultVO;
import com.quant.fund.entity.FundBasic;
import com.quant.fund.entity.FundEtfKline;
import com.quant.fund.entity.FundMaDaily;
import com.quant.fund.entity.FundNav;
import com.quant.fund.enums.FundTypeEnum;
import com.quant.fund.mapper.FundBasicMapper;
import com.quant.fund.mapper.FundEtfKlineMapper;
import com.quant.fund.mapper.FundMaDailyMapper;
import com.quant.fund.mapper.FundNavMapper;
import com.quant.fund.service.FundMaService;
import com.quant.fund.service.TradingCalendarService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 基金日均线服务实现（V5.68）。
 *
 * <p>口径（经用户确认）：MA_n = 最近 n 个交易日价格的**简单平均**；
 * ETF 用前复权收盘价、场外用复权净值（缺失回退单位净值）——与市场信号/基金对比页同源。
 * 落表日期用**行情数据截至日**（每基金各自最新数据日），盘中手动刷新只会更新到最新数据日，
 * 不会产生"日期是今天、价格是昨天"的错位数据。
 *
 * <p>取数：自选池（status=1）全历史价格，ETF/场外各**一条批量 SQL**（同市场信号取数策略）；
 * 计算全部在内存完成——每基金一份**前缀和**，任一日期任一周期的 MA 都是 O(1) 查表，回跑 250 日也秒级。
 * 落表幂等：按 (fund_code, trade_date) 先查已有键，再分"新增/更新"两批（saveBatch/updateBatchById）。
 */
@Service
public class FundMaServiceImpl implements FundMaService {

    /** 均线周期（交易日数）——与【均价】页签列一一对应 */
    private static final int[] MA_WINDOWS = {5, 10, 20, 30, 60, 90, 120, 250};

    /** 回跑交易日数上下限 */
    private static final int BACKFILL_MIN = 5;

    private static final int BACKFILL_MAX = 500;

    private final FundBasicMapper fundBasicMapper;

    private final FundEtfKlineMapper klineMapper;

    private final FundNavMapper navMapper;

    private final FundMaDailyMapper maDailyMapper;

    /** 刷新按钮的"仅交易日"拦截用 */
    private final TradingCalendarService tradingCalendarService;

    public FundMaServiceImpl(FundBasicMapper fundBasicMapper, FundEtfKlineMapper klineMapper,
                             FundNavMapper navMapper, FundMaDailyMapper maDailyMapper,
                             TradingCalendarService tradingCalendarService) {
        this.fundBasicMapper = fundBasicMapper;
        this.klineMapper = klineMapper;
        this.navMapper = navMapper;
        this.maDailyMapper = maDailyMapper;
        this.tradingCalendarService = tradingCalendarService;
    }

    @Override
    public MaRunResultVO refresh() {
        if (!tradingCalendarService.isTradingDay(LocalDate.now())) {
            throw new BizException("今日非交易日，行情无变化，无需刷新均线");
        }
        // targets=null = 每只基金各自取其最新数据日
        return runForDates(null, null);
    }

    @Override
    public MaRunResultVO backfill(int tradingDays) {
        int days = Math.max(BACKFILL_MIN, Math.min(BACKFILL_MAX, tradingDays));
        Map<String, FundSeries> seriesByCode = loadSeries();
        // 交易日集合 = 全部基金价格日期的并集（价格只产生于交易日），升序取末 N 个
        Set<LocalDate> allDates = new TreeSet<>();
        for (FundSeries series : seriesByCode.values()) {
            allDates.addAll(series.dates());
        }
        List<LocalDate> sorted = new ArrayList<>(allDates);
        if (sorted.size() > days) {
            sorted = sorted.subList(sorted.size() - days, sorted.size());
        }
        return runForDates(sorted, seriesByCode);
    }

    @Override
    public List<FundMaVO> latest() {
        List<FundBasic> funds = fundBasicMapper.selectList(
                new LambdaQueryWrapper<FundBasic>().eq(FundBasic::getStatus, 1));
        Map<String, String> nameByCode = new HashMap<>();
        for (FundBasic fund : funds) {
            nameByCode.put(fund.getFundCode(), fund.getFundName());
        }
        // 每基金最新一条：先 group-by 拿各自最大日期，再回捞（与档案刷新同款两步法）
        List<FundMaDaily> maxRows = maDailyMapper.selectList(new QueryWrapper<FundMaDaily>()
                .select("fund_code", "MAX(trade_date) AS trade_date")
                .groupBy("fund_code"));
        List<FundMaVO> result = new ArrayList<>();
        for (FundMaDaily max : maxRows) {
            FundMaDaily row = maDailyMapper.selectOne(new LambdaQueryWrapper<FundMaDaily>()
                    .eq(FundMaDaily::getFundCode, max.getFundCode())
                    .eq(FundMaDaily::getTradeDate, max.getTradeDate())
                    .last("LIMIT 1"));
            if (row == null) {
                continue;
            }
            FundMaVO vo = new FundMaVO();
            vo.setFundCode(row.getFundCode());
            vo.setFundName(nameByCode.getOrDefault(row.getFundCode(), row.getFundCode()));
            vo.setDataDate(row.getTradeDate());
            vo.setClosePrice(row.getClosePrice());
            vo.setMa5(row.getMa5());
            vo.setMa10(row.getMa10());
            vo.setMa20(row.getMa20());
            vo.setMa30(row.getMa30());
            vo.setMa60(row.getMa60());
            vo.setMa90(row.getMa90());
            vo.setMa120(row.getMa120());
            vo.setMa250(row.getMa250());
            vo.setRatio5(ratio(row.getClosePrice(), row.getMa5()));
            vo.setRatio10(ratio(row.getClosePrice(), row.getMa10()));
            vo.setRatio20(ratio(row.getClosePrice(), row.getMa20()));
            vo.setRatio30(ratio(row.getClosePrice(), row.getMa30()));
            vo.setRatio60(ratio(row.getClosePrice(), row.getMa60()));
            vo.setRatio90(ratio(row.getClosePrice(), row.getMa90()));
            vo.setRatio120(ratio(row.getClosePrice(), row.getMa120()));
            vo.setRatio250(ratio(row.getClosePrice(), row.getMa250()));
            result.add(vo);
        }
        result.sort(Comparator.comparing(FundMaVO::getFundCode));
        return result;
    }

    /**
     * 统一落表入口。targets=null → 每基金取各自最新数据日（刷新/定时任务）；
     * targets 非 null → 只算这些交易日（回跑；某基金该日无数据点则该基金该日不落行）。
     */
    private MaRunResultVO runForDates(List<LocalDate> targets, Map<String, FundSeries> loaded) {
        Map<String, FundSeries> seriesByCode = loaded != null ? loaded : loadSeries();
        List<FundBasic> funds = fundBasicMapper.selectList(
                new LambdaQueryWrapper<FundBasic>().eq(FundBasic::getStatus, 1));
        List<FundMaDaily> rows = new ArrayList<>();
        LocalDate min = null;
        LocalDate max = null;
        Set<String> fundCodes = new HashSet<>();
        for (FundBasic fund : funds) {
            FundSeries series = seriesByCode.get(fund.getFundCode());
            if (series == null || series.dates().isEmpty()) {
                continue;
            }
            List<LocalDate> dates = targets != null
                    ? series.datesFor(targets)
                    : List.of(series.dates().get(series.dates().size() - 1));
            for (LocalDate target : dates) {
                FundMaDaily row = buildRow(fund.getFundCode(), target, series);
                if (row == null) {
                    continue;
                }
                rows.add(row);
                fundCodes.add(row.getFundCode());
                if (min == null || row.getTradeDate().isBefore(min)) {
                    min = row.getTradeDate();
                }
                if (max == null || row.getTradeDate().isAfter(max)) {
                    max = row.getTradeDate();
                }
            }
        }
        upsert(rows);
        MaRunResultVO result = new MaRunResultVO();
        result.setFundCount(fundCodes.size());
        result.setRowCount(rows.size());
        result.setDateFrom(min);
        result.setDateTo(max);
        return result;
    }

    /** 单基金单日的快照行：现价 = 该日价格；MA_n = 截至该日 n 根样本的简单平均（样本不足为 null） */
    private FundMaDaily buildRow(String fundCode, LocalDate target, FundSeries series) {
        int floor = series.floorIndex(target);
        if (floor < 0 || !series.dates().get(floor).equals(target)) {
            // 该基金在此日期没有数据点：不落行（避免同一份价格落到两个日期）
            return null;
        }
        FundMaDaily row = new FundMaDaily();
        row.setFundCode(fundCode);
        row.setTradeDate(target);
        row.setClosePrice(series.priceAt(floor));
        row.setMa5(series.maAt(floor, MA_WINDOWS[0]));
        row.setMa10(series.maAt(floor, MA_WINDOWS[1]));
        row.setMa20(series.maAt(floor, MA_WINDOWS[2]));
        row.setMa30(series.maAt(floor, MA_WINDOWS[3]));
        row.setMa60(series.maAt(floor, MA_WINDOWS[4]));
        row.setMa90(series.maAt(floor, MA_WINDOWS[5]));
        row.setMa120(series.maAt(floor, MA_WINDOWS[6]));
        row.setMa250(series.maAt(floor, MA_WINDOWS[7]));
        return row;
    }

    /** 幂等落表：已存在的 (code,date) 按主键走更新，其余新增（批量，整段一个事务） */
    @Transactional
    protected void upsert(List<FundMaDaily> rows) {
        if (rows.isEmpty()) {
            return;
        }
        Set<String> codes = new HashSet<>();
        Set<LocalDate> dates = new HashSet<>();
        for (FundMaDaily row : rows) {
            codes.add(row.getFundCode());
            dates.add(row.getTradeDate());
        }
        Map<String, Long> existingIdByKey = new HashMap<>();
        for (FundMaDaily row : maDailyMapper.selectList(new QueryWrapper<FundMaDaily>()
                .select("id", "fund_code", "trade_date")
                .in("fund_code", codes)
                .in("trade_date", dates))) {
            existingIdByKey.put(row.getFundCode() + "|" + row.getTradeDate(), row.getId());
        }
        List<FundMaDaily> toInsert = new ArrayList<>();
        List<FundMaDaily> toUpdate = new ArrayList<>();
        for (FundMaDaily row : rows) {
            Long id = existingIdByKey.get(row.getFundCode() + "|" + row.getTradeDate());
            if (id != null) {
                row.setId(id);
                toUpdate.add(row);
            } else {
                toInsert.add(row);
            }
        }
        // 逐行写入（回跑 5 年约 4 万行，实测秒级；整段在事务里保证一致性）
        for (FundMaDaily row : toInsert) {
            maDailyMapper.insert(row);
        }
        for (FundMaDaily row : toUpdate) {
            maDailyMapper.updateById(row);
        }
    }

    /** 全历史价格序列：ETF 前复权收盘 / 场外复权净值（缺失回退单位净值），日期升序 */
    private Map<String, FundSeries> loadSeries() {
        Map<String, FundSeries> result = new HashMap<>();
        Map<String, List<LocalDate>> datesByCode = new HashMap<>();
        Map<String, List<BigDecimal>> pricesByCode = new HashMap<>();
        List<FundEtfKline> klines = klineMapper.selectList(new QueryWrapper<FundEtfKline>()
                .select("fund_code", "trade_date", "close")
                .isNotNull("close")
                .orderByAsc("fund_code").orderByAsc("trade_date"));
        for (FundEtfKline row : klines) {
            datesByCode.computeIfAbsent(row.getFundCode(), k -> new ArrayList<>()).add(row.getTradeDate());
            pricesByCode.computeIfAbsent(row.getFundCode(), k -> new ArrayList<>()).add(row.getClose());
        }
        List<FundNav> navs = navMapper.selectList(new LambdaQueryWrapper<FundNav>()
                .select(FundNav::getFundCode, FundNav::getNavDate, FundNav::getUnitNav, FundNav::getAdjNav)
                .orderByAsc(FundNav::getFundCode).orderByAsc(FundNav::getNavDate));
        for (FundNav row : navs) {
            BigDecimal value = row.getAdjNav() != null ? row.getAdjNav() : row.getUnitNav();
            if (value != null) {
                datesByCode.computeIfAbsent(row.getFundCode(), k -> new ArrayList<>()).add(row.getNavDate());
                pricesByCode.computeIfAbsent(row.getFundCode(), k -> new ArrayList<>()).add(value);
            }
        }
        for (Map.Entry<String, List<LocalDate>> entry : datesByCode.entrySet()) {
            result.put(entry.getKey(), FundSeries.of(entry.getValue(), pricesByCode.get(entry.getKey())));
        }
        return result;
    }

    /** 现价 ÷ 均线（3 位小数；均线缺失返回 null） */
    private BigDecimal ratio(BigDecimal close, BigDecimal ma) {
        if (close == null || ma == null || ma.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return close.divide(ma, 3, RoundingMode.HALF_UP);
    }

    /**
     * 单基金价格序列：日期升序 + 前缀和（prefix[i] = 前 i 个价格之和），
     * 任一"截至日 + 周期"的 MA 都是 O(1) 查表——回跑 250 个交易日也无需重复累加。
     */
    private record FundSeries(List<LocalDate> dates, List<BigDecimal> prices, List<BigDecimal> prefix) {

        static FundSeries of(List<LocalDate> dates, List<BigDecimal> prices) {
            List<BigDecimal> prefix = new ArrayList<>(prices.size() + 1);
            prefix.add(BigDecimal.ZERO);
            for (BigDecimal price : prices) {
                prefix.add(prefix.get(prefix.size() - 1).add(price));
            }
            return new FundSeries(List.copyOf(dates), List.copyOf(prices), List.copyOf(prefix));
        }

        /** 最后一个 date <= target 的下标（二分）；无则 -1 */
        int floorIndex(LocalDate target) {
            int lo = 0;
            int hi = dates.size() - 1;
            int floor = -1;
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                if (dates.get(mid).compareTo(target) <= 0) {
                    floor = mid;
                    lo = mid + 1;
                } else {
                    hi = mid - 1;
                }
            }
            return floor;
        }

        BigDecimal priceAt(int index) {
            return prices.get(index);
        }

        /** 截至下标 floor（含）的 n 个交易日简单平均；样本不足返回 null */
        BigDecimal maAt(int floor, int n) {
            if (floor + 1 < n) {
                return null;
            }
            return prefix.get(floor + 1).subtract(prefix.get(floor + 1 - n))
                    .divide(BigDecimal.valueOf(n), 4, RoundingMode.HALF_UP);
        }

        /** targets 里落在该基金数据范围内（首日之后）的日期子集 */
        List<LocalDate> datesFor(List<LocalDate> targets) {
            LocalDate first = dates.get(0);
            return targets.stream().filter(d -> !d.isBefore(first)).toList();
        }
    }
}
