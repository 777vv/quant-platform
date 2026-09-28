package com.quant.strategy.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.quant.common.exception.BizException;
import com.quant.common.result.PageResult;
import com.quant.common.util.JsonUtils;
import com.quant.fund.entity.FundBasic;
import com.quant.fund.entity.FundPosition;
import com.quant.fund.entity.SyncLog;
import com.quant.fund.enums.SyncTypeEnum;
import com.quant.fund.entity.TradeFlow;
import com.quant.fund.mapper.FundBasicMapper;
import com.quant.fund.mapper.FundPositionMapper;
import com.quant.fund.mapper.TradeFlowMapper;
import com.quant.fund.mapper.SyncLogMapper;
import com.quant.strategy.core.MarketDataLoader;
import com.quant.strategy.core.MarketDataSeries;
import com.quant.strategy.core.Signal;
import com.quant.strategy.core.Strategy;
import com.quant.strategy.core.StrategyContext;
import com.quant.strategy.core.StrategyRegistry;
import com.quant.strategy.dto.SignalItemVO;
import com.quant.strategy.entity.SignalRecord;
import com.quant.strategy.entity.StrategyConfig;
import com.quant.strategy.grid.AbstractGridStrategy;
import com.quant.strategy.oscillation.OscillatingUpStrategy;
import com.quant.strategy.mapper.SignalRecordMapper;
import com.quant.strategy.mapper.StrategyConfigMapper;
import com.quant.strategy.service.SignalService;
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

    /** 未识别策略类型的兜底回看天数（自然日）：给足近段行情，不因窗口不足而算不出信号 */
    private static final int DEFAULT_WINDOW_DAYS = 120;

    /** 分页查询单页条数上限（防一次性拉全表） */
    private static final long PAGE_SIZE_LIMIT = 200;

    /** 建议描述最长保留长度（列宽 255） */
    private static final int DESC_MAX_LEN = 255;

    private final StrategyConfigMapper configMapper;

    private final SignalRecordMapper signalRecordMapper;

    private final SyncLogMapper syncLogMapper;

    private final MarketDataLoader marketDataLoader;

    private final StrategyRegistry registry;

    /** 基金档案（分页查询时批量解析基金名称用） */
    private final FundBasicMapper fundBasicMapper;

    /** 持仓汇总（震荡向上按实际持仓判断档位） */
    private final FundPositionMapper positionMapper;

    /** 交易流水（震荡向上以最近一次实际买卖为状态锚点） */
    private final TradeFlowMapper tradeFlowMapper;

    public SignalServiceImpl(StrategyConfigMapper configMapper, SignalRecordMapper signalRecordMapper,
                             SyncLogMapper syncLogMapper, MarketDataLoader marketDataLoader,
                             StrategyRegistry registry, FundBasicMapper fundBasicMapper,
                             FundPositionMapper positionMapper, TradeFlowMapper tradeFlowMapper) {
        this.configMapper = configMapper;
        this.signalRecordMapper = signalRecordMapper;
        this.syncLogMapper = syncLogMapper;
        this.marketDataLoader = marketDataLoader;
        this.registry = registry;
        this.fundBasicMapper = fundBasicMapper;
        this.positionMapper = positionMapper;
        this.tradeFlowMapper = tradeFlowMapper;
    }

    @Override
    public List<SignalRecord> generateAll() {
        LocalDateTime startAt = LocalDateTime.now();
        List<StrategyConfig> configs = configMapper.selectList(new LambdaQueryWrapper<StrategyConfig>()
                .eq(StrategyConfig::getEnabled, 1));
        List<SignalRecord> signals = new ArrayList<>(configs.size());
        List<String> errors = new ArrayList<>();
        for (StrategyConfig config : configs) {
            // 已下线策略（如旧的 GRID/VAL_PERCENTILE）可能还留有计划任务读不到的配置行：
            // 跳过并只记一条 warn，不写进 sync_log 的失败清单（否则每天都刷一条"未知策略类型"的错误）
            if (!registry.contains(config.getStrategyType())) {
                LOGGER.warn("策略类型[{}]已下线，跳过基金[{}]的这条配置（可在【策略配置】里删除）",
                        config.getStrategyType(), config.getFundCode());
                continue;
            }
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
    public PageResult<SignalItemVO> page(String fundCode, String direction, String strategyType,
            LocalDate startDate, LocalDate endDate, long page, long size) {
        // 每页条数上限收紧（防一次性拉全表），页码从 1 起
        Page<SignalRecord> result = signalRecordMapper.selectPage(
                Page.of(Math.max(page, 1), Math.min(Math.max(size, 1), PAGE_SIZE_LIMIT)),
                new LambdaQueryWrapper<SignalRecord>()
                        .eq(hasText(fundCode), SignalRecord::getFundCode, trimToNull(fundCode))
                        .eq(hasText(direction), SignalRecord::getDirection, trimToNull(direction))
                        .eq(hasText(strategyType), SignalRecord::getStrategyType, trimToNull(strategyType))
                        .ge(startDate != null, SignalRecord::getSignalDate, startDate)
                        .le(endDate != null, SignalRecord::getSignalDate, endDate)
                        .orderByDesc(SignalRecord::getSignalDate).orderByAsc(SignalRecord::getFundCode));
        List<SignalRecord> records = result.getRecords();
        // 本页出现的基金代码一次查回名称（避免逐行查库）；查不到的行前端回退显示代码
        Map<String, String> names = new HashMap<>();
        if (!records.isEmpty()) {
            List<String> codes = records.stream().map(SignalRecord::getFundCode).distinct().toList();
            fundBasicMapper.selectList(new LambdaQueryWrapper<FundBasic>()
                    .in(FundBasic::getFundCode, codes))
                    .forEach(fund -> names.put(fund.getFundCode(), fund.getFundName()));
        }
        List<SignalItemVO> items = records.stream()
                .map(record -> SignalItemVO.of(record, names.getOrDefault(record.getFundCode(), ""),
                        strategyName(record.getStrategyType())))
                .toList();
        return PageResult.of(result.getTotal(), items);
    }

    /** 策略展示名（未知类型回退为类型码，与邮件摘要同一口径） */
    private String strategyName(String strategyType) {
        try {
            return registry.getRequired(strategyType).name();
        } catch (BizException e) {
            return strategyType;
        }
    }

    /** 非空判断（null 或全空白视为空条件） */
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /** trim 到空即 null（避免把空白拼进查询条件） */
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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
        LocalDate signalDate = series.get(series.size() - 1).date();
        // 当前仓位与最近一次实际交易：供需持仓状态的策略使用（震荡向上的档位与首次/二次判断）。
        // ⚠️ 锚点是【实际交易流水】而非信号——信号发了没照做（无流水）即不算数，状态机以成交为准（用户口径 V5.9）
        BigDecimal currentShares = currentShares(config.getFundCode());
        TradeFlow lastTrade = lastTrade(config.getFundCode());
        Signal signal = strategy.generateSignal(new StrategyContext(loaded.fund(), params, series,
                currentShares,
                lastTrade == null ? null : (lastTrade.getTradeType() == 1 ? Signal.BUY : Signal.SELL),
                lastTrade == null ? null : lastTrade.getPrice(),
                lastTrade == null ? null : lastTrade.getTradeDate()));
        return upsert(config, signalDate, signal);
    }

    /**
     * 当前持仓份额（平台记录的实际持仓；无持仓返回 0）。
     * 口径：用户手动记录的交易流水汇总（fund_position.total_share），策略只是读取、不做推算。
     */
    private BigDecimal currentShares(String fundCode) {
        FundPosition position = positionMapper.selectOne(new LambdaQueryWrapper<FundPosition>()
                .eq(FundPosition::getFundCode, fundCode));
        return position == null || position.getTotalShare() == null ? BigDecimal.ZERO : position.getTotalShare();
    }

    /**
     * 最近一次实际买卖交易（trade_flow，只认 买入/卖出 两种类型，分红与划转不算）。
     * 无记录返回 null = "起步"状态（震荡向上按首次分支、用 windowDays 窗口处理）。
     */
    private TradeFlow lastTrade(String fundCode) {
        return tradeFlowMapper.selectOne(new LambdaQueryWrapper<TradeFlow>()
                .eq(TradeFlow::getFundCode, fundCode)
                .in(TradeFlow::getTradeType, List.of(1, 2))
                .orderByDesc(TradeFlow::getTradeDate)
                .orderByDesc(TradeFlow::getId)
                .last("limit 1"));
    }

    /** 窗口天数：震荡向上按 K线天数；网格族按趋势均线天数；其余策略给兜底窗口 */
    private int windowDays(String strategyType, JsonNode params) {
        if (OscillatingUpStrategy.TYPE.equals(strategyType)) {
            // loadRecent 收的是自然日：A 股年约 243 个交易日，按 1.6 倍 + 30 天冗余，保证窗口内至少有 K线天数 根 bar
            return (int) Math.ceil(Strategy.intOr(params, "windowDays", 60) * 1.6) + 30;
        }
        if (AbstractGridStrategy.isGridType(strategyType)) {
            // 网格族：MA 闸门需要约 90 个自然日，与回测预热（warmupDaysOf）保持同一口径
            int maDays = Math.max(Strategy.intOr(params, "trendMaDays", 60), 60);
            return (int) Math.ceil(maDays * 1.6) + 30;
        }
        return DEFAULT_WINDOW_DAYS;
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
