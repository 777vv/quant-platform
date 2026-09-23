package com.quant.strategy.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.quant.common.exception.BizException;
import com.quant.common.result.PageResult;
import com.quant.common.util.JsonUtils;
import com.quant.strategy.core.BacktestEngine;
import com.quant.strategy.core.FeeProperties;
import com.quant.strategy.core.IndicatorCalculator;
import com.quant.strategy.core.MarketDataLoader;
import com.quant.strategy.core.MarketDataSeries;
import com.quant.strategy.core.Strategy;
import com.quant.strategy.core.StrategyRegistry;
import com.quant.strategy.dto.BacktestRequest;
import com.quant.strategy.entity.BacktestRecord;
import com.quant.strategy.entity.BacktestTradeDetail;
import com.quant.strategy.mapper.BacktestRecordMapper;
import com.quant.strategy.mapper.BacktestTradeDetailMapper;
import com.quant.strategy.service.BacktestService;
import com.quant.strategy.oscillation.OscillatingUpStrategy;
import com.quant.strategy.valuation.ValPercentileStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;

/**
 * 回测服务实现：异步执行 → 结果与曲线永久落库（FR2）。
 * 估值百分位策略需窗口预热：加载时向前多取 windowYears，保存时按请求区间裁剪曲线与明细。
 */
@Service
public class BacktestServiceImpl implements BacktestService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BacktestServiceImpl.class);

    private final BacktestRecordMapper recordMapper;

    private final BacktestTradeDetailMapper tradeMapper;

    private final StrategyRegistry registry;

    private final MarketDataLoader dataLoader;

    private final BacktestEngine engine;

    private final IndicatorCalculator indicatorCalculator;

    private final FeeProperties feeProperties;

    public BacktestServiceImpl(BacktestRecordMapper recordMapper, BacktestTradeDetailMapper tradeMapper,
                               StrategyRegistry registry, MarketDataLoader dataLoader, BacktestEngine engine,
                               IndicatorCalculator indicatorCalculator, FeeProperties feeProperties) {
        this.recordMapper = recordMapper;
        this.tradeMapper = tradeMapper;
        this.registry = registry;
        this.dataLoader = dataLoader;
        this.engine = engine;
        this.indicatorCalculator = indicatorCalculator;
        this.feeProperties = feeProperties;
    }

    @Override
    public Long create(BacktestRequest request) {
        validate(request);
        Strategy strategy = registry.getRequired(request.getStrategyType());
        String paramsJson = JsonUtils.toJson(request.getParams() == null ? Map.of() : request.getParams());
        strategy.validateParams(JsonUtils.mapper().readTree(paramsJson));
        BacktestRecord record = new BacktestRecord();
        record.setFundCode(request.getFundCode());
        record.setStrategyType(request.getStrategyType());
        record.setParams(paramsJson);
        record.setStartDate(request.getStartDate());
        record.setEndDate(request.getEndDate());
        record.setInitialCapital(request.getInitialCapital());
        record.setStatus(BacktestRecord.STATUS_RUNNING);
        record.setTradeCount(0);
        recordMapper.insert(record);
        runBacktest(record.getId(), record.getFundCode(), strategy, JsonUtils.mapper().readTree(paramsJson),
                request.getStartDate(), request.getEndDate(), request.getInitialCapital());
        return record.getId();
    }

    /**
     * 回测预热天数（自然日）：策略按"根数"回看时须换算，保证首个决策日就有足够历史。
     * 估值百分位按 windowYears；震荡向上按 K线天数（×1.6 + 30 冗余）；其余策略只需近期行情。
     */
    private int warmupDaysOf(String strategyType, JsonNode params) {
        if (ValPercentileStrategy.TYPE.equals(strategyType)) {
            return params.path("windowYears").asInt(10) * 366 + 60;
        }
        if (OscillatingUpStrategy.TYPE.equals(strategyType)) {
            return (int) Math.ceil(params.path("windowDays").asInt(60) * 1.6) + 30;
        }
        return 30;
    }

    @Async("taskExecutor")
    protected void runBacktest(Long recordId, String fundCode, Strategy strategy, JsonNode params,
                               LocalDate startDate, LocalDate endDate, BigDecimal initialCapital) {
        try {
            int warmupDays = warmupDaysOf(strategy.type(), params);
            MarketDataLoader.LoadedData loaded = dataLoader.load(fundCode, startDate, endDate, warmupDays);
            MarketDataSeries series = loaded.series();
            int startIndex = 0;
            for (int i = 0; i < series.size(); i++) {
                if (!series.get(i).date().isBefore(startDate)) {
                    startIndex = i;
                    break;
                }
            }
            // 策略级前置校验（如震荡向上：本金必须买得起满仓份额，否则档位规则失真）
            strategy.validateBacktest(series, startIndex, params, initialCapital);
            BacktestEngine.BacktestResult result = engine.execute(strategy, params, series, initialCapital, startIndex);
            // 裁剪至请求区间（warmup 段仅用于估值窗口）
            List<Object[]> equity = trim(result.equityCurve(), startDate);
            List<Object[]> drawdown = trim(result.drawdownCurve(), startDate);
            List<Object[]> benchmark = trim(result.benchmarkCurve(), startDate);
            List<BacktestTradeDetail> trades = result.trades().stream()
                    .filter(trade -> trade.getTradeDate() == null || !trade.getTradeDate().isBefore(startDate))
                    .toList();
            IndicatorCalculator.Metrics metrics = indicatorCalculator.calculate(equity, initialCapital,
                    feeProperties.getRiskFreeRate(), trades.size(), result.sellCount(), result.winCount());
            // 买入持有基准的区间指标：复用同一指标器（基准曲线、期初资金、无交易）
            IndicatorCalculator.Metrics bench = indicatorCalculator.calculate(benchmark, initialCapital,
                    0, 0, 0, 0);
            BacktestRecord update = new BacktestRecord();
            update.setId(recordId);
            update.setFinalAssets(result.finalAssets());
            update.setAvgPositionShare(result.avgPositionShare());
            update.setAvgPositionValue(result.avgPositionValue());
            update.setAvgPositionCost(result.avgPositionCost());
            update.setPositionReturnPct(result.positionReturnPct());
            update.setBenchTotalReturnPct(bench.totalReturnPct());
            update.setBenchMaxDrawdownPct(bench.maxDrawdownPct());
            update.setTotalReturnPct(metrics.totalReturnPct());
            update.setAnnualizedPct(metrics.annualizedPct());
            update.setMaxDrawdownPct(metrics.maxDrawdownPct());
            update.setDdPeakDate(metrics.ddPeakDate());
            update.setDdTroughDate(metrics.ddTroughDate());
            update.setDdRecoverDate(metrics.ddRecoverDate());
            update.setSharpe(metrics.sharpe());
            update.setWinRate(metrics.winRate());
            update.setTradeCount(metrics.tradeCount());
            update.setStatus(BacktestRecord.STATUS_SUCCESS);
            update.setEquityCurve(JsonUtils.toJson(equity));
            update.setDrawdownCurve(JsonUtils.toJson(drawdown));
            update.setBenchmarkCurve(JsonUtils.toJson(benchmark));
            recordMapper.updateById(update);
            trades.forEach(trade -> trade.setBacktestId(recordId));
            if (!trades.isEmpty()) {
                Db.saveBatch(trades, 500);
            }
            LOGGER.info("回测[{}]完成：{} 笔交易，总收益 {}%", recordId, metrics.tradeCount(),
                    metrics.totalReturnPct());
        } catch (Exception e) {
            LOGGER.error("回测[{}]失败", recordId, e);
            BacktestRecord fail = new BacktestRecord();
            fail.setId(recordId);
            fail.setStatus(BacktestRecord.STATUS_FAILED);
            fail.setErrorMsg(e.getMessage() == null ? "未知错误" : e.getMessage().substring(0,
                    Math.min(e.getMessage().length(), 500)));
            recordMapper.updateById(fail);
        }
    }

    @Override
    public BacktestRecord detail(Long id) {
        BacktestRecord record = recordMapper.selectById(id);
        if (record == null) {
            throw new BizException("回测记录不存在");
        }
        return record;
    }

    @Override
    public PageResult<BacktestRecord> page(String fundCode, long page, long size) {
        IPage<BacktestRecord> result = recordMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<BacktestRecord>()
                        .eq(fundCode != null && !fundCode.isBlank(), BacktestRecord::getFundCode, fundCode)
                        .orderByDesc(BacktestRecord::getId)
                        .select(BacktestRecord.class, field -> !field.getColumn().contains("curve")));
        return PageResult.of(result.getTotal(), result.getRecords());
    }

    @Override
    public PageResult<BacktestTradeDetail> trades(Long backtestId, long page, long size) {
        IPage<BacktestTradeDetail> result = tradeMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<BacktestTradeDetail>()
                        .eq(BacktestTradeDetail::getBacktestId, backtestId)
                        .orderByAsc(BacktestTradeDetail::getTradeDate));
        return PageResult.of(result.getTotal(), result.getRecords());
    }

    private void validate(BacktestRequest request) {
        if (request.getFundCode() == null || request.getStrategyType() == null || request.getStartDate() == null
                || request.getEndDate() == null || request.getInitialCapital() == null) {
            throw new BizException("回测参数不完整");
        }
        if (!request.getEndDate().isAfter(request.getStartDate())) {
            throw new BizException("结束日期须晚于开始日期");
        }
        if (request.getInitialCapital().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("初始资金须大于 0");
        }
    }

    private List<Object[]> trim(List<Object[]> curve, LocalDate startDate) {
        List<Object[]> trimmed = new ArrayList<>(curve.size());
        for (Object[] point : curve) {
            if (!((LocalDate) point[0]).isBefore(startDate)) {
                trimmed.add(point);
            }
        }
        return trimmed;
    }
}
