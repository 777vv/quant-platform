package com.quant.strategy.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.quant.common.util.JsonUtils;
import com.quant.fund.entity.SyncLog;
import com.quant.fund.enums.SyncTypeEnum;
import com.quant.fund.mapper.SyncLogMapper;
import com.quant.strategy.core.MarketDataLoader;
import com.quant.strategy.core.MarketDataSeries;
import com.quant.strategy.core.Signal;
import com.quant.strategy.core.Strategy;
import com.quant.strategy.core.StrategyContext;
import com.quant.strategy.core.StrategyRegistry;
import com.quant.strategy.entity.SignalRecord;
import com.quant.strategy.entity.StrategyConfig;
import com.quant.strategy.grid.GridStrategy;
import com.quant.strategy.mapper.SignalRecordMapper;
import com.quant.strategy.mapper.StrategyConfigMapper;
import com.quant.strategy.service.SignalService;
import com.quant.strategy.valuation.ValPercentileStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 策略信号服务实现（FR5，M4-03）。
 * 关键设计：
 * 1) 幂等：以 (fund_code, strategy_type, signal_date) 唯一键 upsert，signal_date 取行情序列最后一日，
 *    净值未公布时自动落到最近可得日，次日新数据到达会生成新纪录，不丢不重；
 * 2) 网格锚点稳定：GRID 策略首跑时若未配置 anchorPrice，将默认锚点回写进策略配置，
 *    避免滚动窗口起点漂移导致相邻两日信号跳变；
 * 3) 窗口长度按策略类型区分：估值百分位需要 windowYears 回看，网格只需近期行情。
 */
@Service
public class SignalServiceImpl implements SignalService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SignalServiceImpl.class);

    /** 网格策略实时信号回看天数（锚点已持久化，窗口只需覆盖近段行情） */
    private static final int GRID_WINDOW_DAYS = 120;

    /** 估值策略单年回看天数（自然日，含节假日冗余） */
    private static final int VAL_WINDOW_DAYS_PER_YEAR = 370;

    /** 建议描述最长保留长度（列宽 255） */
    private static final int DESC_MAX_LEN = 255;

    private final StrategyConfigMapper configMapper;

    private final SignalRecordMapper signalRecordMapper;

    private final SyncLogMapper syncLogMapper;

    private final MarketDataLoader marketDataLoader;

    private final StrategyRegistry registry;

    public SignalServiceImpl(StrategyConfigMapper configMapper, SignalRecordMapper signalRecordMapper,
                             SyncLogMapper syncLogMapper, MarketDataLoader marketDataLoader,
                             StrategyRegistry registry) {
        this.configMapper = configMapper;
        this.signalRecordMapper = signalRecordMapper;
        this.syncLogMapper = syncLogMapper;
        this.marketDataLoader = marketDataLoader;
        this.registry = registry;
    }

    @Override
    public List<SignalRecord> generateAll() {
        LocalDateTime startAt = LocalDateTime.now();
        List<StrategyConfig> configs = configMapper.selectList(new LambdaQueryWrapper<StrategyConfig>()
                .eq(StrategyConfig::getEnabled, 1));
        List<SignalRecord> signals = new ArrayList<>(configs.size());
        List<String> errors = new ArrayList<>();
        for (StrategyConfig config : configs) {
            try {
                signals.add(generateOne(config));
            } catch (Exception e) {
                LOGGER.error("策略[{}#{}]信号计算失败", config.getFundCode(), config.getStrategyType(), e);
                errors.add(config.getFundCode() + "/" + config.getStrategyType() + ":" + e.getMessage());
            }
        }
        writeLog(errors, signals.size(), startAt);
        return signals;
    }

    @Override
    public List<SignalRecord> recent(int days) {
        return signalRecordMapper.selectList(new LambdaQueryWrapper<SignalRecord>()
                .ge(SignalRecord::getSignalDate, LocalDate.now().minusDays(days))
                .orderByDesc(SignalRecord::getSignalDate).orderByAsc(SignalRecord::getFundCode));
    }

    @Override
    public void markRead(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        LambdaUpdateWrapper<SignalRecord> wrapper = new LambdaUpdateWrapper<SignalRecord>()
                .in(SignalRecord::getId, ids)
                .eq(SignalRecord::getReadFlag, 0)
                .set(SignalRecord::getReadFlag, 1);
        signalRecordMapper.update(null, wrapper);
    }

    /** 单策略信号计算：加载窗口数据 → （网格）锚点首跑回写 → 生成信号 → 幂等 upsert */
    private SignalRecord generateOne(StrategyConfig config) {
        Strategy strategy = registry.getRequired(config.getStrategyType());
        JsonNode params = JsonUtils.mapper().readTree(config.getParams());
        MarketDataLoader.LoadedData loaded = marketDataLoader.loadRecent(
                config.getFundCode(), windowDays(config.getStrategyType(), params));
        MarketDataSeries series = loaded.series();
        if (series.size() == 0) {
            throw new IllegalStateException("基金[" + config.getFundCode() + "]无行情数据");
        }
        if (GridStrategy.TYPE.equals(config.getStrategyType()) && !params.has("anchorPrice")) {
            BigDecimal anchor = GridStrategy.defaultAnchor(params, series);
            ((ObjectNode) params).put("anchorPrice", anchor);
            config.setParams(JsonUtils.toJson(params));
            configMapper.updateById(config);
        }
        Signal signal = strategy.generateSignal(new StrategyContext(loaded.fund(), params, series));
        LocalDate signalDate = series.get(series.size() - 1).date();
        return upsert(config, signalDate, signal);
    }

    /** 窗口天数：估值百分位按 windowYears 回看；网格只需近期行情 */
    private int windowDays(String strategyType, JsonNode params) {
        if (ValPercentileStrategy.TYPE.equals(strategyType)) {
            return Strategy.intOr(params, "windowYears", 10) * VAL_WINDOW_DAYS_PER_YEAR;
        }
        return GRID_WINDOW_DAYS;
    }

    /** 幂等写入：同 (基金, 策略, 信号日) 已存在则仅更新信号内容，保留已读/已通知标记 */
    private SignalRecord upsert(StrategyConfig config, LocalDate signalDate, Signal signal) {
        SignalRecord record = signalRecordMapper.selectOne(new LambdaQueryWrapper<SignalRecord>()
                .eq(SignalRecord::getFundCode, config.getFundCode())
                .eq(SignalRecord::getStrategyType, config.getStrategyType())
                .eq(SignalRecord::getSignalDate, signalDate));
        boolean exists = record != null;
        if (!exists) {
            record = new SignalRecord();
            record.setFundCode(config.getFundCode());
            record.setStrategyType(config.getStrategyType());
            record.setSignalDate(signalDate);
            record.setReadFlag(0);
            record.setNotifiedFlag(0);
        }
        record.setDirection(signal.direction());
        record.setPriceAt(signal.price());
        String desc = signal.suggestDesc() == null ? "" : signal.suggestDesc();
        record.setSuggestDesc(desc.substring(0, Math.min(desc.length(), DESC_MAX_LEN)));
        if (exists) {
            signalRecordMapper.updateById(record);
        } else {
            signalRecordMapper.insert(record);
        }
        return record;
    }

    /** 写 sync_log：全部成功 status=1，否则 0 并记录失败清单 */
    private void writeLog(List<String> errors, int count, LocalDateTime startAt) {
        SyncLog log = new SyncLog();
        log.setSyncType(SyncTypeEnum.SIGNAL.name());
        log.setStatus(errors.isEmpty() ? 1 : 0);
        log.setRecordCount(count);
        if (!errors.isEmpty()) {
            String joined = String.join(" | ", errors);
            log.setErrorMsg(joined.substring(0, Math.min(joined.length(), 500)));
        }
        log.setStartTime(startAt);
        log.setEndTime(LocalDateTime.now());
        syncLogMapper.insert(log);
    }
}
