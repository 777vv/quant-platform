package com.quant.strategy.core;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.quant.common.exception.BizException;
import com.quant.fund.entity.FundBasic;
import com.quant.fund.entity.FundEtfKline;
import com.quant.fund.entity.FundNav;
import com.quant.fund.entity.IndexValuation;
import com.quant.fund.enums.FundTypeEnum;
import com.quant.fund.mapper.FundBasicMapper;
import com.quant.fund.mapper.FundEtfKlineMapper;
import com.quant.fund.mapper.FundNavMapper;
import com.quant.fund.mapper.IndexValuationMapper;
import org.springframework.stereotype.Component;

/**
 * 行情数据加载器：ETF 前复权K线 / 场外单位净值 统一为 MarketDataSeries，并按跟踪指数关联 PE
 */
@Component
public class MarketDataLoader {

    private final FundBasicMapper fundBasicMapper;

    private final FundEtfKlineMapper klineMapper;

    private final FundNavMapper navMapper;

    private final IndexValuationMapper valuationMapper;

    public MarketDataLoader(FundBasicMapper fundBasicMapper, FundEtfKlineMapper klineMapper,
                            FundNavMapper navMapper, IndexValuationMapper valuationMapper) {
        this.fundBasicMapper = fundBasicMapper;
        this.klineMapper = klineMapper;
        this.navMapper = navMapper;
        this.valuationMapper = valuationMapper;
    }

    public record LoadedData(FundBasic fund, MarketDataSeries series) {
    }

    /** 回测加载：start 前追加 warmupDays 预热（估值百分位窗口用），输出含预热段由上层裁剪 */
    public LoadedData load(String fundCode, LocalDate start, LocalDate end, int warmupDays) {
        FundBasic fund = fundBasicMapper.selectOne(
                new LambdaQueryWrapper<FundBasic>().eq(FundBasic::getFundCode, fundCode));
        if (fund == null || fund.getStatus() != 1) {
            throw new BizException("基金不在自选池: " + fundCode);
        }
        LocalDate loadStart = warmupDays > 0 ? start.minusDays(warmupDays) : start;
        List<MarketDataSeries.DayPoint> points = new ArrayList<>();
        if (FundTypeEnum.ETF.getCode() == fund.getFundType()) {
            List<FundEtfKline> rows = klineMapper.selectList(new LambdaQueryWrapper<FundEtfKline>()
                    .eq(FundEtfKline::getFundCode, fundCode)
                    .between(FundEtfKline::getTradeDate, loadStart, end)
                    .orderByAsc(FundEtfKline::getTradeDate));
            rows.forEach(row -> points.add(new MarketDataSeries.DayPoint(
                    row.getTradeDate(), row.getOpen(), row.getClose(), null)));
        } else {
            List<FundNav> rows = navMapper.selectList(new LambdaQueryWrapper<FundNav>()
                    .eq(FundNav::getFundCode, fundCode)
                    .between(FundNav::getNavDate, loadStart, end)
                    .orderByAsc(FundNav::getNavDate));
            rows.forEach(row -> points.add(new MarketDataSeries.DayPoint(
                    row.getNavDate(), row.getUnitNav(), row.getUnitNav(), null)));
        }
        if (points.isEmpty()) {
            throw new BizException("基金[" + fundCode + "]在区间内无行情数据");
        }
        joinPe(fund, points);
        return new LoadedData(fund, new MarketDataSeries(
                FundTypeEnum.ETF.getCode() == fund.getFundType(), points));
    }

    /** 实时信号加载：近 windowDays 个交易日 */
    public LoadedData loadRecent(String fundCode, int windowDays) {
        return load(fundCode, LocalDate.now().minusDays(windowDays), LocalDate.now(), 0);
    }

    private void joinPe(FundBasic fund, List<MarketDataSeries.DayPoint> points) {
        if (fund.getIndexCode() == null || points.isEmpty()) {
            return;
        }
        List<IndexValuation> valuations = valuationMapper.selectList(
                new LambdaQueryWrapper<IndexValuation>()
                        .eq(IndexValuation::getIndexCode, fund.getIndexCode())
                        .between(IndexValuation::getTradeDate, points.get(0).date(),
                                points.get(points.size() - 1).date())
                        .isNotNull(IndexValuation::getPe));
        Map<LocalDate, java.math.BigDecimal> peMap = new HashMap<>();
        valuations.forEach(row -> peMap.put(row.getTradeDate(), row.getPe()));
        for (int i = 0; i < points.size(); i++) {
            MarketDataSeries.DayPoint old = points.get(i);
            points.set(i, new MarketDataSeries.DayPoint(old.date(), old.open(), old.close(), peMap.get(old.date())));
        }
    }
}
