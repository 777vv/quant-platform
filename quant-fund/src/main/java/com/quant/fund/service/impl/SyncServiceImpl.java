package com.quant.fund.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.quant.common.exception.BizException;
import com.quant.common.util.JsonUtils;
import com.quant.common.util.LockUtils;
import com.quant.fund.client.EastmoneyClient;
import com.quant.fund.config.DashboardProperties;
import com.quant.fund.dto.TaskProgressVO;
import com.quant.fund.entity.FundBasic;
import com.quant.fund.entity.FundEtfKline;
import com.quant.fund.entity.FundNav;
import com.quant.fund.entity.FundPosition;
import com.quant.fund.entity.IndexQuote;
import com.quant.fund.entity.IndexValuation;
import com.quant.fund.entity.SyncLog;
import com.quant.fund.enums.FundTypeEnum;
import com.quant.fund.enums.SyncTypeEnum;
import com.quant.fund.mapper.FundBasicMapper;
import com.quant.fund.mapper.FundEtfKlineMapper;
import com.quant.fund.mapper.FundNavMapper;
import com.quant.fund.mapper.FundPositionMapper;
import com.quant.fund.mapper.IndexQuoteMapper;
import com.quant.fund.mapper.IndexValuationMapper;
import com.quant.fund.mapper.SyncLogMapper;
import com.quant.fund.service.DividendService;
import com.quant.fund.service.FundScaleHistoryService;
import com.quant.fund.service.SyncService;
import com.quant.fund.service.TaskProgressStore;
import com.quant.fund.service.TradingCalendarService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 数据同步服务实现（FR5）。
 * 关键口径：last_sync_date 始终等于"本地最新数据日期"（而非任务执行日），
 * 保证净值未公布时的次日补拉不漏数据；ETF 定时任务全量覆盖修正前复权口径（技术文档 6.3）。
 */
@Service
public class SyncServiceImpl implements SyncService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SyncServiceImpl.class);

    private static final String CACHE_INDEX_QUOTES = "quote:index:list";

    /** 指数近 30 交易日走势的 Redis 键前缀（后接 secid，仪表盘迷你线数据源） */
    private static final String TREND_KEY_PREFIX = "quote:index:trend30:";

    /** 迷你线采样点数：近 30 个交易日 */
    private static final int TREND_DAYS = 30;

    /** 迷你线缓存时长：6 小时（日线收盘价日内不变，避免 5 分钟任务重复拉取） */
    private static final Duration TREND_TTL = Duration.ofHours(6);

    /** 迷你线构建总时间预算（毫秒）：超预算即停，避免装饰性数据拖慢看板；剩余由下次刷新补齐 */
    private static final long TREND_BUILD_BUDGET_MS = 12_000L;

    /** 迷你线降级标记的存活时长（分钟）：短于走势缓存，便于封堵结束后尽快重试 */
    private static final Duration TREND_DEGRADED_TTL = Duration.ofMinutes(10);

    /** 行情快照降级标记（值为 1 表示当前显示的是库内快照而非实时拉取） */
    private static final String CACHE_INDEX_DEGRADED = "quote:index:degraded";

    /** 迷你线缺失标记的 Redis 键（值为 1 表示本轮有缺失，供看板展示降级提示） */
    private static final String CACHE_TREND_DEGRADED = "quote:index:trend:degraded";

    /** 迷你线拉取窗口：自然日，覆盖 30 个交易日所需的日历跨度 */
    private static final int TREND_FETCH_DAYS = 50;

    private static final String SOURCE_CSINDEX = "CSINDEX";

    private static final int NAV_PAGE_SIZE = 20;

    private final EastmoneyClient client;

    private final FundBasicMapper fundBasicMapper;

    private final FundEtfKlineMapper klineMapper;

    private final FundNavMapper navMapper;

    private final IndexValuationMapper valuationMapper;

    private final IndexQuoteMapper indexQuoteMapper;

    private final SyncLogMapper syncLogMapper;

    /** 持仓表（自动同步按持仓/非持仓分流） */
    private final FundPositionMapper positionMapper;

    /** 分红记录（行情图【q】标记的数据源，随档案每日刷新） */
    private final DividendService dividendService;

    /** 基金规模历史（档案刷新成功后逐日落一行） */
    private final FundScaleHistoryService scaleHistoryService;

    /** 交易日判定（V5.24）：休市名单 + 周末，节假日不再空跑外部请求 */
    private final TradingCalendarService tradingCalendarService;

    private final TaskProgressStore progressStore;

    private final TransactionTemplate transactionTemplate;

    private final DashboardProperties dashboardProperties;

    private final LockUtils lockUtils;

    private final StringRedisTemplate redisTemplate;

    public SyncServiceImpl(EastmoneyClient client, FundBasicMapper fundBasicMapper, FundEtfKlineMapper klineMapper,
                           FundNavMapper navMapper, IndexValuationMapper valuationMapper,
                           IndexQuoteMapper indexQuoteMapper, SyncLogMapper syncLogMapper,
                           TaskProgressStore progressStore, TransactionTemplate transactionTemplate,
                           DashboardProperties dashboardProperties, LockUtils lockUtils,
                           StringRedisTemplate redisTemplate, FundPositionMapper positionMapper,
                           DividendService dividendService, FundScaleHistoryService scaleHistoryService,
                           TradingCalendarService tradingCalendarService) {
        this.client = client;
        this.fundBasicMapper = fundBasicMapper;
        this.klineMapper = klineMapper;
        this.navMapper = navMapper;
        this.valuationMapper = valuationMapper;
        this.indexQuoteMapper = indexQuoteMapper;
        this.syncLogMapper = syncLogMapper;
        this.progressStore = progressStore;
        this.transactionTemplate = transactionTemplate;
        this.dashboardProperties = dashboardProperties;
        this.lockUtils = lockUtils;
        this.redisTemplate = redisTemplate;
        this.positionMapper = positionMapper;
        this.dividendService = dividendService;
        this.scaleHistoryService = scaleHistoryService;
        this.tradingCalendarService = tradingCalendarService;
    }

    @Override
    public String manualSync(String fundCode) {
        FundBasic fund = fundBasicMapper.selectOne(
                new LambdaQueryWrapper<FundBasic>().eq(FundBasic::getFundCode, fundCode));
        if (fund == null || fund.getStatus() != 1) {
            throw new BizException("基金不在自选池: " + fundCode);
        }
        String taskId = UUID.randomUUID().toString().replace("-", "");
        progressStore.save(new TaskProgressVO(taskId, TaskProgressVO.RUNNING, "开始增量同步", 0, 0, null));
        runManualSync(taskId, fund);
        return taskId;
    }

    @Override
    public TaskProgressVO manualProgress(String taskId) {
        TaskProgressVO progress = progressStore.get(taskId);
        if (progress == null) {
            throw new BizException("任务不存在或已过期");
        }
        return progress;
    }

    @Async("taskExecutor")
    protected void runManualSync(String taskId, FundBasic fund) {
        try {
            // 手动同步强制刷档案（用户明确点了同步，希望立刻看到规模/费率）
            int added = syncFundData(fund);
            progressStore.save(new TaskProgressVO(taskId, TaskProgressVO.DONE,
                    added > 0 ? "同步完成，新增 " + added + " 条" : "同步完成，无新数据", 0, added, null));
        } catch (Exception e) {
            LOGGER.error("基金[{}]手动同步失败", fund.getFundCode(), e);
            writeLog(SyncTypeEnum.MANUAL, fund.getFundCode(), false, 0, e.getMessage());
            progressStore.save(new TaskProgressVO(taskId, TaskProgressVO.FAILED, "同步失败", 0, 0, e.getMessage()));
        }
    }

    @Override
    public void syncAllEtfDaily() {
        lockUtils.runWithLock("job:etf:daily", 30, () -> {
            LocalDateTime startAt = LocalDateTime.now();
            List<FundBasic> funds = fundBasicMapper.selectList(new LambdaQueryWrapper<FundBasic>()
                    .eq(FundBasic::getStatus, 1).eq(FundBasic::getFundType, FundTypeEnum.ETF.getCode()));
            int ok = 0;
            List<String> errors = new ArrayList<>();
            for (FundBasic fund : funds) {
                try {
                    syncFundFullCover(fund);
                    ok++;
                } catch (Exception e) {
                    LOGGER.error("ETF[{}]日K覆盖同步失败", fund.getFundCode(), e);
                    errors.add(fund.getFundCode() + ":" + e.getMessage());
                }
            }
            writeLog(SyncTypeEnum.ETF_DAILY, null, errors.isEmpty(), ok,
                    errors.isEmpty() ? null : String.join(" | ", errors), startAt);
        });
    }

    @Override
    public void syncAllOtcNav() {
        lockUtils.runWithLock("job:nav", 30, () -> {
            LocalDateTime startAt = LocalDateTime.now();
            List<FundBasic> funds = fundBasicMapper.selectList(new LambdaQueryWrapper<FundBasic>()
                    .eq(FundBasic::getStatus, 1).eq(FundBasic::getFundType, FundTypeEnum.OTC.getCode()));
            int updated = 0;
            List<String> errors = new ArrayList<>();
            for (FundBasic fund : funds) {
                try {
                    syncFundData(fund, false);
                    updated++;
                } catch (Exception e) {
                    LOGGER.error("场外[{}]净值同步失败", fund.getFundCode(), e);
                    errors.add(fund.getFundCode() + ":" + e.getMessage());
                }
            }
            writeLog(SyncTypeEnum.NAV, null, errors.isEmpty(), updated,
                    errors.isEmpty() ? null : String.join(" | ", errors), startAt);
        });
    }

    @Override
    public void syncValuation() {
        lockUtils.runWithLock("job:valuation", 30, () -> {
            LocalDateTime startAt = LocalDateTime.now();
            List<String> indexCodes = fundBasicMapper.selectList(new LambdaQueryWrapper<FundBasic>()
                            .eq(FundBasic::getStatus, 1).isNotNull(FundBasic::getIndexCode)).stream()
                    .map(FundBasic::getIndexCode).distinct().toList();
            int ok = 0;
            List<String> errors = new ArrayList<>();
            for (String indexCode : indexCodes) {
                try {
                    incrementalValuation(indexCode);
                    ok++;
                } catch (Exception e) {
                    LOGGER.error("指数[{}]估值同步失败", indexCode, e);
                    errors.add(indexCode + ":" + e.getMessage());
                }
            }
            writeLog(SyncTypeEnum.VALUATION, null, errors.isEmpty(), ok,
                    errors.isEmpty() ? null : String.join(" | ", errors), startAt);
        });
    }

    @Override
    public void refreshIndexQuotes() {
        refreshIndexQuotes(false);
    }

    @Override
    public void refreshIndexQuotes(boolean force) {
        // 走势缓存先行补齐：与行情快照解耦，避免快照接口抖动导致迷你线长期为空
        ensureDailyTrend(dashboardProperties.getIndices(), force);
        List<String> secids = dashboardProperties.getIndices().stream()
                .map(DashboardProperties.IndexItem::getSecid).toList();
        List<EastmoneyClient.IndexQuoteItem> items = client.fetchIndexQuotes(secids);
        Map<String, DashboardProperties.IndexItem> configMap = new LinkedHashMap<>();
        for (DashboardProperties.IndexItem item : dashboardProperties.getIndices()) {
            configMap.put(item.getSecid(), item);
        }
        List<IndexQuote> quotes = new ArrayList<>();
        for (EastmoneyClient.IndexQuoteItem item : items) {
            DashboardProperties.IndexItem config = configMap.get(item.secid());
            if (config == null) {
                continue;
            }
            IndexQuote quote = new IndexQuote();
            quote.setIndexCode(item.secid());
            quote.setIndexName(config.getName());
            quote.setRegion(config.getRegion());
            quote.setLastPrice(item.price());
            quote.setChangeAmt(item.changeAmt());
            quote.setChangePct(item.changePct());
            quote.setQuoteTime(item.quoteTime() > 0
                    ? LocalDateTime.ofInstant(java.time.Instant.ofEpochSecond(item.quoteTime()),
                            java.time.ZoneId.of("Asia/Shanghai"))
                    : null);
            quotes.add(quote);
        }
        if (quotes.isEmpty()) {
            throw new BizException("全球指数行情拉取为空");
        }
        redisTemplate.opsForValue().set(CACHE_INDEX_DEGRADED, "0", Duration.ofMinutes(10));
        transactionTemplate.executeWithoutResult(status -> {
            indexQuoteMapper.delete(new LambdaQueryWrapper<>());
            quotes.forEach(indexQuoteMapper::insert);
        });
        redisTemplate.opsForValue().set(CACHE_INDEX_QUOTES, "1", Duration.ofMinutes(6));
    }

    /**
     * 确保各指数的近 30 交易日走势缓存存在（仪表盘迷你线数据源）。
     * 缓存命中即跳过；使用**快速拉取**（主域 + 1 个备用域、不重试）——迷你线属装饰性数据，
     * 不能让多域名长退避把看板请求拖成长任务。
     * 失败策略：**逐个数继续尝试**而非"连续失败即放弃"——实测封堵按域名波动，
     * 中途跳过反而错过恢复窗口；仅以总时间预算兜底，超预算即停，剩余由下一次刷新补齐。
     *
     * @param force 用户手动刷新时传 true：忽略降级窗口强制重试（仍受总预算约束）
     */
    private void ensureDailyTrend(List<DashboardProperties.IndexItem> indices, boolean force) {
        // 已知数据源处于封堵窗口：跳过本轮尝试（10 分钟标记过期后自动重试），
        // 避免每次打开看板都白等一次构建预算；用户手动点"刷新"不受此限制
        if (!force && "1".equals(redisTemplate.opsForValue().get(CACHE_TREND_DEGRADED))) {
            LOGGER.info("迷你线处于降级窗口，本轮跳过构建");
            return;
        }
        long deadline = System.currentTimeMillis() + TREND_BUILD_BUDGET_MS;
        int succeeded = 0;
        int failed = 0;
        for (DashboardProperties.IndexItem item : indices) {
            String key = TREND_KEY_PREFIX + item.getSecid();
            if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
                succeeded++;
                continue;
            }
            if (System.currentTimeMillis() > deadline) {
                LOGGER.info("迷你线构建超出 {}ms 预算，剩余指数留待下次刷新补齐", TREND_BUILD_BUDGET_MS);
                break;
            }
            try {
                String[] parts = item.getSecid().split("\\.");
                if (parts.length != 2) {
                    continue;
                }
                List<EastmoneyClient.KlineItem> items = client.fetchEtfKlineFast(
                        Integer.parseInt(parts[0]), parts[1],
                        LocalDate.now().minusDays(TREND_FETCH_DAYS), LocalDate.now());
                if (items.isEmpty()) {
                    continue;
                }
                List<TrendSample> samples = items.stream()
                        .skip(Math.max(0, items.size() - TREND_DAYS))
                        .map(kline -> new TrendSample(kline.date().toString(), kline.close()))
                        .toList();
                redisTemplate.opsForValue().set(key, JsonUtils.toJson(samples), TREND_TTL);
                succeeded++;
            } catch (Exception e) {
                failed++;
                LOGGER.error("指数[{}]走势采样拉取失败", item.getSecid(), e);
            }
        }
        if (failed > 0) {
            LOGGER.info("迷你线构建结束：可用 {} 个，失败 {} 个（下次行情刷新自动补齐）", succeeded, failed);
        }
        // 记录本轮缺失情况，供看板给出"数据源降级"提示（前端据此展示离线快照说明）
        redisTemplate.opsForValue().set(CACHE_TREND_DEGRADED, failed > 0 ? "1" : "0", TREND_DEGRADED_TTL);
    }

    @Override
    public List<IndexQuote> getIndexQuotes() {
        // V2.2 缓存优先：仪表盘只读库内快照（秒开），新鲜度由定时任务维护；
        // 库为空（首次启动且任务未跑）时才同步触发一次刷新，避免看板空白
        if (indexQuoteMapper.selectCount(new LambdaQueryWrapper<>()) == 0) {
            try {
                refreshIndexQuotes();
            } catch (Exception e) {
                LOGGER.error("全球指数行情首次拉取失败", e);
                redisTemplate.opsForValue().set(CACHE_INDEX_DEGRADED, "1", Duration.ofMinutes(10));
            }
        }
        List<IndexQuote> all = indexQuoteMapper.selectList(new LambdaQueryWrapper<>());
        Map<String, IndexQuote> byCode = new LinkedHashMap<>();
        all.forEach(quote -> byCode.put(quote.getIndexCode(), quote));
        List<IndexQuote> ordered = new ArrayList<>();
        for (DashboardProperties.IndexItem item : dashboardProperties.getIndices()) {
            IndexQuote quote = byCode.get(item.getSecid());
            if (quote != null) {
                ordered.add(quote);
            }
        }
        return ordered;
    }

    /** ETF 手动增量：自 last_sync_date 次日起 upsert（含当日盘中未定型数据） */
    private int syncEtfIncremental(FundBasic fund) {
        LocalDate today = LocalDate.now();
        LocalDate cursor = fund.getLastSyncDate() == null
                ? ImportServiceImpl.historyBegin(fund.getInceptionDate())
                : fund.getLastSyncDate().plusDays(1);
        // 当天的 K 线是"未定稿"的：开盘后数据源就给当天一行，但盘中会一直变，直到 15:00 收盘才是终值。
        // 首轮同步写入当天那行后游标会推到今天，若照上面直接 +1 天，beg 就落到明天 →
        // 拉取区间为空 → 当天价格永远停在第一次写入的那一刻（实测 515080 停在 09:30 的开盘价），
        // 手动点"同步"也只会得到"无新数据"。故把下界夹在今天：当天的行每轮都重写一遍。
        // 注：beg 被下面的 lambda 捕获，必须是有效最终变量，故用一次性求值而不是分支里重新赋值。
        LocalDate beg = cursor.isAfter(today) ? today : cursor;
        int market = "SH".equals(fund.getMarket()) ? 1 : 0;
        List<EastmoneyClient.KlineItem> klines = client.fetchEtfKline(market, fund.getFundCode(), beg, LocalDate.now());
        int added = 0;
        if (!klines.isEmpty()) {
            List<FundEtfKline> entities = klines.stream().map(item -> toKlineEntity(fund.getFundCode(), item)).toList();
            transactionTemplate.executeWithoutResult(status -> {
                klineMapper.delete(new LambdaQueryWrapper<FundEtfKline>()
                        .eq(FundEtfKline::getFundCode, fund.getFundCode())
                        .ge(FundEtfKline::getTradeDate, beg));
                Db.saveBatch(entities, 500);
            });
            updateLastSync(fund, entities.get(entities.size() - 1).getTradeDate());
            added = entities.size();
        }
        return added;
    }

    /**
     * 刷新基础数据（每次数据同步后调用）：
     * <ol>
     *   <li><b>档案类</b>（规模/费率/跟踪指数）：每天最多拉一次档案页（按 profile_sync_date 判断）——
     *       这些字段一天内不变，而盘中每 10 分钟同步一次，逐次拉 HTML 页纯属浪费且拉长任务耗时；
     *       每交易日 15:05 有一次强制刷新（用户口径：收盘后立即再刷一次最新规模）；</li>
     *   <li><b>分红记录</b>：独立的 dividend_sync_date 标记——只有"抓取成功"才算刷过，
     *       失败（数据源封堵等）次日自动重试，直到成功为止（V5.3 前失败会被静默吞掉，
     *       510300 的分红因此长期缺数、股息率一直显示 "--"）；</li>
     *   <li><b>溢价率</b>：场内 ETF 每次同步都重算（价格与净值都会变）。</li>
     * </ol>
     * 全部失败都只记日志，不影响行情/净值同步结果。
     */
    private void refreshBasicData(FundBasic fund, boolean forceProfile) {
        refreshProfileIfNeeded(fund, forceProfile);
        refreshDividendsIfNeeded(fund, forceProfile);
        if (fund.getFundType() != null && fund.getFundType() == FundTypeEnum.ETF.getCode()) {
            refreshPremiumRate(fund);
            // 每日估算规模（V6.15）：份额 × 当日单位净值——与季报披露同口径、但每个交易日可得
            estimateEtfDailyScale(fund);
        }
    }

    /**
     * 档案类基础数据（规模/费率/跟踪指数）刷新。
     * 自动同步（含盘中每 10 分钟）每天最多刷一次；**手动点"同步"时强制刷**——
     * 手动是明确的用户意图，且能让当天导入/变更的基金立刻看到规模与费率。
     *
     * @param force true=忽略"当天已刷"标记，强制重新拉取档案页
     */
    private void refreshProfileIfNeeded(FundBasic fund, boolean force) {
        if (!force && LocalDate.now().equals(fund.getProfileSyncDate())) {
            return;
        }
        try {
            EastmoneyClient.FundProfile profile = client.fetchFundProfile(fund.getFundCode());
            fundBasicMapper.update(null, new LambdaUpdateWrapper<FundBasic>()
                    .eq(FundBasic::getFundCode, fund.getFundCode())
                    .set(FundBasic::getFundScale, profile.fundScale())
                    .set(FundBasic::getFundScaleDate, profile.fundScaleDate())
                    .set(FundBasic::getMgmtFeeRate, profile.mgmtFeeRate())
                    .set(FundBasic::getCustFeeRate, profile.custFeeRate())
                    .set(FundBasic::getSalesFeeRate, profile.salesFeeRate())
                    .set(FundBasic::getProfileSyncDate, LocalDate.now()));
            fund.setFundScale(profile.fundScale());
            fund.setFundScaleDate(profile.fundScaleDate());
            fund.setMgmtFeeRate(profile.mgmtFeeRate());
            fund.setCustFeeRate(profile.custFeeRate());
            fund.setSalesFeeRate(profile.salesFeeRate());
            fund.setProfileSyncDate(LocalDate.now());
            // 规模历史随每次成功的档案刷新落一行（幂等，同日覆盖），供基金规模走势副图使用
            scaleHistoryService.record(fund.getFundCode(), profile.fundScale(), profile.fundScaleDate());
        } catch (Exception e) {
            LOGGER.error("基金[{}]档案（规模/费率）刷新失败", fund.getFundCode(), e);
        }
    }

    /**
     * 分红记录刷新（股息率数据的分子）。
     * 与档案解耦、拥有独立的成功标记 dividend_sync_date：
     * <ul>
     *   <li>抓取成功（含"该基金确实无分红"）→ 写标记，当天不再重复拉；</li>
     *   <li>抓取失败 → 不写标记并告警，同一天内的后续同步与次日同步都会重试。</li>
     * </ul>
     * 行情图【q】标记只读库里已落好的记录，抓取绝不放在看图请求路径上。
     *
     * @param force true=忽略标记强制刷新（手动同步）
     */
    private void refreshDividendsIfNeeded(FundBasic fund, boolean force) {
        if (!force && LocalDate.now().equals(fund.getDividendSyncDate())) {
            return;
        }
        try {
            dividendService.refresh(fund.getFundCode());
            fundBasicMapper.update(null, new LambdaUpdateWrapper<FundBasic>()
                    .eq(FundBasic::getFundCode, fund.getFundCode())
                    .set(FundBasic::getDividendSyncDate, LocalDate.now()));
            fund.setDividendSyncDate(LocalDate.now());
        } catch (Exception e) {
            LOGGER.error("基金[{}]分红记录刷新失败（股息率可能缺失/滞后，下次同步自动重试）",
                    fund.getFundCode(), e);
        }
    }

    /**
     * 每日估算规模（V6.15，用户口径）：**场内 ETF 规模 = 当日份额 × 当日单位净值**（亿元）。
     *
     * <p>为什么需要：东财 f10 的「净资产规模」是**定期报告口径**（如截止 6/30），一个季度才变一次，
     * 列表/副图看着像"数据不动"；而场内 ETF 的**份额每交易日公布**（东财行情 f84），
     * 乘当日单位净值（与溢价率同一净值来源）即可得到与披露口径一致、但每日更新的规模估算。
     *
     * <p>落库：写进 fund_scale_history（source=ESTIMATED，stat_date=净值日），与披露值(DISCLOSED)区分；
     * 份额或净值取不到时**跳过并记日志**（不影响同步主流程，次日重试）。
     * 场外基金（无每日份额数据源）不参与，继续用季报口径。
     */
    private void estimateEtfDailyScale(FundBasic fund) {
        try {
            Integer market = "SH".equals(fund.getMarket()) ? 1 : 0;
            java.util.Optional<BigDecimal> shares = client.fetchEtfShare(market, fund.getFundCode());
            if (shares.isEmpty()) {
                return;
            }
            List<EastmoneyClient.NavItem> navs =
                    client.fetchOtcNavPage(fund.getFundCode(), 1, 1, null, null).items();
            if (navs.isEmpty() || navs.get(0).unitNav() == null
                    || navs.get(0).unitNav().compareTo(BigDecimal.ZERO) <= 0) {
                return;
            }
            EastmoneyClient.NavItem latest = navs.get(0);
            // 规模（亿元）= 份额（份） × 单位净值（元） ÷ 1e8
            BigDecimal scaleYi = shares.get().multiply(latest.unitNav())
                    .divide(BigDecimal.valueOf(100000000L), 2, RoundingMode.HALF_UP);
            scaleHistoryService.recordEstimated(fund.getFundCode(), latest.date(), scaleYi);
            LOGGER.info("基金[{}]每日规模估算：份额 {} 份 × 净值 {}（{}） = {} 亿元",
                    fund.getFundCode(), shares.get().toPlainString(), latest.unitNav().toPlainString(),
                    latest.date(), scaleYi.toPlainString());
        } catch (Exception e) {
            LOGGER.error("基金[{}]每日规模估算失败（次日重试）", fund.getFundCode(), e);
        }
    }

    /**
     * 计算溢价率 =（当日收盘价 − 当日单位净值）/ 当日单位净值 × 100%。
     * <p>取"最新单位净值日"当天的收盘价，保证分子分母是同一天：净值当日收盘后才公布，
     * 若直接用最新价配昨日净值会得到虚高的溢价率，反而误导。
     * 该净值日在本地无 K 线（节假日等）时跳过，保留上一次的值。
     */
    private void refreshPremiumRate(FundBasic fund) {
        try {
            List<EastmoneyClient.NavItem> navs =
                    client.fetchOtcNavPage(fund.getFundCode(), 1, 1, null, null).items();
            if (navs.isEmpty() || navs.get(0).unitNav() == null
                    || navs.get(0).unitNav().compareTo(BigDecimal.ZERO) <= 0) {
                return;
            }
            EastmoneyClient.NavItem latest = navs.get(0);
            FundEtfKline kline = klineMapper.selectOne(new LambdaQueryWrapper<FundEtfKline>()
                    .eq(FundEtfKline::getFundCode, fund.getFundCode())
                    .eq(FundEtfKline::getTradeDate, latest.date()));
            if (kline == null) {
                return;
            }
            BigDecimal premium = kline.getClose().subtract(latest.unitNav())
                    .multiply(BigDecimal.valueOf(100))
                    .divide(latest.unitNav(), 4, RoundingMode.HALF_UP);
            fundBasicMapper.update(null, new LambdaUpdateWrapper<FundBasic>()
                    .eq(FundBasic::getFundCode, fund.getFundCode())
                    .set(FundBasic::getPremiumRate, premium)
                    .set(FundBasic::getPremiumDate, latest.date()));
            fund.setPremiumRate(premium);
            fund.setPremiumDate(latest.date());
        } catch (Exception e) {
            LOGGER.error("基金[{}]溢价率计算失败", fund.getFundCode(), e);
        }
    }

    /**
     * 同步一只基金的数据（统一入口）：按类型走 ETF / 场外 增量路径，
     * **基础数据（档案/规模费率/分红/溢价率）在 finally 中刷新**——行情抓取受数据源限流失败时，
     * 档案与分红仍能更新，不被行情失败连坐（此前把刷新写在方法尾部，一旦行情抛异常就整段跳过）。
     */
    private int syncFundData(FundBasic fund) {
        return syncFundData(fund, true);
    }

    /**
     * 同步一只基金的数据（统一入口）。
     *
     * @param forceProfile 是否强制刷新档案（手动同步传 true；定时任务传 false 以遵守"每日一次"）
     */
    private int syncFundData(FundBasic fund, boolean forceProfile) {
        try {
            if (fund.getFundType() != null && fund.getFundType() == FundTypeEnum.ETF.getCode()) {
                int added = syncEtfIncremental(fund);
                if (forceProfile) {
                    // 手动同步（V5.26）：顺带回填缺失的未复权收盘价（股息率分母），
                    // 让本版之前导入、unadj_close 有缺口的老基金在点一次同步后自愈
                    backfillUnadjustedClose(fund);
                }
                return added;
            }
            return syncOtcIncremental(fund);
        } finally {
            refreshBasicData(fund, forceProfile);
        }
    }

    /**
     * 回填缺失的未复权收盘价（只补 unadj_close 为空的行，一次区间请求）。
     * 失败仅记日志（股息率继续显示"--"，下次同步重试），不影响主同步流程。
     */
    private void backfillUnadjustedClose(FundBasic fund) {
        List<FundEtfKline> missing = klineMapper.selectList(new LambdaQueryWrapper<FundEtfKline>()
                .eq(FundEtfKline::getFundCode, fund.getFundCode())
                .isNull(FundEtfKline::getUnadjClose));
        if (missing.isEmpty()) {
            return;
        }
        try {
            int market = "SH".equals(fund.getMarket()) ? 1 : 0;
            Map<LocalDate, BigDecimal> unadjusted = client
                    .fetchEtfKlineUnadjusted(market, fund.getFundCode(), missing.get(0).getTradeDate(), LocalDate.now())
                    .stream()
                    .collect(Collectors.toMap(EastmoneyClient.KlineItem::date, EastmoneyClient.KlineItem::close,
                            (first, second) -> first));
            int fixed = 0;
            for (FundEtfKline row : missing) {
                BigDecimal close = unadjusted.get(row.getTradeDate());
                if (close != null) {
                    row.setUnadjClose(close);
                    fixed++;
                }
            }
            if (fixed > 0) {
                transactionTemplate.executeWithoutResult(status -> Db.updateBatchById(missing, 500));
                LOGGER.info("基金[{}]回填未复权收盘价 {} 行（股息率分母）", fund.getFundCode(), fixed);
            }
        } catch (Exception e) {
            LOGGER.error("基金[{}]未复权价回填失败（下次同步重试）", fund.getFundCode(), e);
        }
    }

    /**
     * 每日全量覆盖路径（ETF 定时任务）：同样保证基础数据在行情失败时也刷新。
     *
     * @param fund 基金
     */
    private void syncFundFullCover(FundBasic fund) {
        try {
            fullCoverEtf(fund);
        } finally {
            refreshBasicData(fund, false);
        }
    }

    /**
     * 补齐未复权收盘价（历史股息率的分母）。
     *
     * <p>前复权价把历史价格压低（等于把分红从价格里抹掉），直接拿它当分母会**系统性高估历史股息率**，
     * 因此每次全量覆盖额外拉一次 fqt=0 并按交易日对齐写入。
     * 这一步失败（数据源封堵等）只记日志、留空：前端对缺失值按"--"处理，待数据源恢复后自动补齐，
     * **绝不能让装饰性数据把主同步流程带崩**。
     */
    private void fillUnadjustedClose(FundBasic fund, int market, LocalDate beg, List<FundEtfKline> entities) {
        try {
            Map<LocalDate, BigDecimal> unadjusted = client
                    .fetchEtfKlineUnadjusted(market, fund.getFundCode(), beg, LocalDate.now())
                    .stream()
                    .collect(Collectors.toMap(EastmoneyClient.KlineItem::date, EastmoneyClient.KlineItem::close,
                            (first, second) -> first));
            if (unadjusted.isEmpty()) {
                return;
            }
            entities.forEach(entity -> entity.setUnadjClose(unadjusted.get(entity.getTradeDate())));
        } catch (Exception e) {
            LOGGER.error("基金[{}]未复权价拉取失败（历史股息率将暂不可用，下次同步重试）",
                    fund.getFundCode(), e);
        }
    }

    /** ETF 全量覆盖（定时任务用，修正前复权口径） */
    private void fullCoverEtf(FundBasic fund) {
        LocalDate beg = ImportServiceImpl.historyBegin(fund.getInceptionDate());
        int market = "SH".equals(fund.getMarket()) ? 1 : 0;
        List<EastmoneyClient.KlineItem> klines = client.fetchEtfKline(market, fund.getFundCode(), beg, LocalDate.now());
        if (klines.isEmpty()) {
            return;
        }
        List<FundEtfKline> entities = klines.stream().map(item -> toKlineEntity(fund.getFundCode(), item)).toList();
        fillUnadjustedClose(fund, market, beg, entities);
        transactionTemplate.executeWithoutResult(status -> {
            klineMapper.delete(new LambdaQueryWrapper<FundEtfKline>()
                    .eq(FundEtfKline::getFundCode, fund.getFundCode()));
            Db.saveBatch(entities, 500);
        });
        updateLastSync(fund, entities.get(entities.size() - 1).getTradeDate());
    }

    /** 场外净值增量：以区间前最后一行为基准链接复权净值；结束后刷新档案类基础数据 */
    private int syncOtcIncremental(FundBasic fund) {
        LocalDate today = LocalDate.now();
        LocalDate cursor = fund.getLastSyncDate() == null
                ? ImportServiceImpl.historyBegin(fund.getInceptionDate())
                : fund.getLastSyncDate().plusDays(1);
        // 与 ETF 同理把下界夹在今天：净值一旦写入当天，游标即到当天，此后不带夹取就永远拉不到当天，
        // 若数据源当天晚些时候才发布/修正当日净值，补拉（次日 07:00）就会漏掉它。（beg 被 lambda 捕获，须有效最终）
        LocalDate beg = cursor.isAfter(today) ? today : cursor;
        List<EastmoneyClient.NavItem> items = new ArrayList<>();
        int pageIndex = 1;
        while (pageIndex <= 20) {
            EastmoneyClient.NavPage page = client.fetchOtcNavPage(fund.getFundCode(), pageIndex, NAV_PAGE_SIZE,
                    beg, LocalDate.now());
            items.addAll(page.items());
            if (!page.hasNext() || page.items().isEmpty()) {
                break;
            }
            pageIndex++;
        }
        if (items.isEmpty()) {
            return 0;
        }
        items.sort(Comparator.comparing(EastmoneyClient.NavItem::date));
        FundNav base = navMapper.selectOne(new LambdaQueryWrapper<FundNav>()
                .eq(FundNav::getFundCode, fund.getFundCode())
                .lt(FundNav::getNavDate, beg)
                .orderByDesc(FundNav::getNavDate)
                .last("limit 1"));
        List<FundNav> entities = ImportServiceImpl.buildNavWithAdj(fund.getFundCode(), items, base);
        transactionTemplate.executeWithoutResult(status -> {
            navMapper.delete(new LambdaQueryWrapper<FundNav>()
                    .eq(FundNav::getFundCode, fund.getFundCode())
                    .ge(FundNav::getNavDate, beg));
            Db.saveBatch(entities, 500);
        });
        updateLastSync(fund, entities.get(entities.size() - 1).getNavDate());
        return entities.size();
    }

    /** 指数估值增量：自本地最大估值日期次日起 */
    private void incrementalValuation(String indexCode) {
        IndexValuation latest = valuationMapper.selectOne(new LambdaQueryWrapper<IndexValuation>()
                .eq(IndexValuation::getIndexCode, indexCode)
                .orderByDesc(IndexValuation::getTradeDate)
                .last("limit 1"));
        LocalDate beg = latest == null ? ImportServiceImpl.historyBegin(null) : latest.getTradeDate().plusDays(1);
        List<EastmoneyClient.ValuationItem> items = client.fetchIndexValuation(indexCode, beg, LocalDate.now());
        List<IndexValuation> entities = items.stream().filter(item -> item.pe() != null
                        && !item.date().equals(latest == null ? null : latest.getTradeDate()))
                .map(item -> {
                    IndexValuation row = new IndexValuation();
                    row.setIndexCode(indexCode);
                    row.setTradeDate(item.date());
                    row.setPe(item.pe());
                    row.setSource(SOURCE_CSINDEX);
                    return row;
                }).toList();
        if (entities.isEmpty()) {
            return;
        }
        transactionTemplate.executeWithoutResult(status -> Db.saveBatch(entities, 500));
        LOGGER.info("指数[{}]估值增量 {} 条", indexCode, entities.size());
    }

    /** 交易日自判的 Redis 键前缀（值 pending/confirmed，键按自然日，自动过期由 TTL 保证） */
    private static final String HOLIDAY_KEY_PREFIX = "market:holiday:";

    /** 交易日自判"待确认"的观察时长（秒）：连续两次观察都无当天 bar 才判节假日 */
    private static final long HOLIDAY_PENDING_SECONDS = 900;

    /** 盘中同步封堵窗口暂停时长（毫秒）：连续 3 只失败后暂停探窗，避免逐只烧重试退避 */
    private static final long INTRADAY_PAUSE_MILLIS = 90_000;

    /**
     * 盘中同步·持仓基金（V5.42，用户口径：池子扩到几百只后，5 分钟全量一轮跑不完，拆两档）：
     * 持仓基金每 4 分钟一轮（调度在 FundSyncJobs，交易时段闸门在任务层）。
     * 服务层只管"跑哪个范围"：交易日/节假日自判 + 互斥锁 + 范围过滤 + 逐只增量同步。
     * 手动触发（SyncController cases=held）随时可用，便于补一轮。
     */
    @Override
    public void syncHeldFundsIntraday() {
        runIntradaySync("job:sync:held", true);
    }

    /**
     * 盘中同步·自选中的非持仓基金（V5.42）：每 30 分钟一轮，cron 与持仓轮次错开 3 分钟防重叠。
     * 场外基金净值没有盘中口径，不参加（仍走 20:00 / 次日 07:00 的净值同步）。
     */
    @Override
    public void syncNonHeldFundsIntraday() {
        runIntradaySync("job:sync:others", false);
    }

    /**
     * 盘中增量同步核心：按 heldOnly 过滤本轮范围（持仓 = fund_position.total_share > 0）。
     *
     * <p>交易日自判（数据源自身回答"今天开不开市"）：同步成功（HTTP 正常）但库里没有今天这根 bar
     * → 今天疑似节假日；连续两轮观察（间隔 ≥ 15 分钟）都没有 → 确认为节假日并写 Redis 标记
     * （自然日过期），当天剩余时间不再发起任何请求。请求失败（封堵/网络）不做结论，下轮自然重试。
     */
    private void runIntradaySync(String lockName, boolean heldOnly) {
        if (isMarketClosedToday()) {
            return;
        }
        String scope = heldOnly ? "持仓" : "非持仓";
        lockUtils.runWithLock(lockName, heldOnly ? 30 : 60, () -> {
            LocalDateTime startAt = LocalDateTime.now();
            List<FundBasic> all = fundBasicMapper.selectList(new LambdaQueryWrapper<FundBasic>()
                    .eq(FundBasic::getStatus, 1).eq(FundBasic::getFundType, FundTypeEnum.ETF.getCode()));
            Set<String> held = holdingCodes();
            // heldOnly=true 取持仓段，false 取非持仓段（一份全集拆两轮，互不重复）
            List<FundBasic> funds = all.stream()
                    .filter(fund -> held.contains(fund.getFundCode()) == heldOnly)
                    .toList();
            int ok = 0;
            List<String> errors = new ArrayList<>();
            int consecutiveFailures = 0;
            for (FundBasic fund : funds) {
                try {
                    syncFundData(fund, false);
                    ok++;
                    consecutiveFailures = 0;
                } catch (Exception e) {
                    consecutiveFailures++;
                    LOGGER.error("盘中同步[{}][{}]失败", scope, fund.getFundCode(), e);
                    errors.add(fund.getFundCode() + ":" + e.getMessage());
                    // 封堵自适应（V5.42，与批量导入同款）：连续 3 只失败（每只内部已重试 3 次）判定进入
                    // 封堵窗口，暂停 90 秒再继续——否则封堵期内每只都要烧完 7 秒退避，几百只时一轮跑不完
                    if (consecutiveFailures >= 3) {
                        LOGGER.warn("盘中同步[{}]连续 {} 只失败，判定进入数据源封堵窗口，暂停 {}ms 后继续", scope, consecutiveFailures, INTRADAY_PAUSE_MILLIS);
                        try {
                            Thread.sleep(INTRADAY_PAUSE_MILLIS);
                            consecutiveFailures = 0;
                        } catch (InterruptedException ie) {
                            // 中断是信号不是错误（铁律 13 唯一例外）：恢复中断位，继续处理剩余基金
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            }
            // 交易日自判：全部同步成功但没有任何一只出现今天的 bar → 记一次"疑似节假日"观察
            if (errors.isEmpty() && !funds.isEmpty() && !hasTodayBar(funds.get(0).getFundCode())) {
                observeHoliday();
            } else if (!funds.isEmpty() && hasTodayBar(funds.get(0).getFundCode())) {
                // 出现当天 bar = 今天确定开市，清掉可能存在的"疑似"标记
                redisTemplate.delete(HOLIDAY_KEY_PREFIX + LocalDate.now());
            }
            writeLog(SyncTypeEnum.AUTO, null, errors.isEmpty(), ok,
                    errors.isEmpty() ? null : scope + "轮: " + String.join(" | ", errors), startAt);
            LOGGER.info("盘中同步[{}]完成：{} 只（成功 {}，失败 {}）", scope, funds.size(), ok, errors.size());
        });
    }

    /** 持仓基金代码集合（fund_position.total_share > 0；与基金池列表的持仓判定同口径） */
    private Set<String> holdingCodes() {
        return positionMapper.selectList(new LambdaQueryWrapper<FundPosition>()
                        .gt(FundPosition::getTotalShare, BigDecimal.ZERO))
                .stream().map(FundPosition::getFundCode).collect(Collectors.toSet());
    }

    /**
     * 今日是否休市（V5.24）：两层判定，任一命中即休市——
     * ① 休市名单（周末 + 法定节假日安排，确定性，见 {@link TradingCalendarService}）；
     * ② 盘面自判（数据源自身回答"今天开不开市"，用于名单未覆盖的临时休市）。
     */
    private boolean isMarketClosedToday() {
        if (!tradingCalendarService.isTradingDay(LocalDate.now())) {
            LOGGER.info("今日非交易日（周末/休市名单），任务跳过");
            return true;
        }
        if (isConfirmedHoliday()) {
            LOGGER.info("今日判定为节假日（数据源自判），任务跳过");
            return true;
        }
        return false;
    }

    /** 今天已被确认为节假日（数据源自判） */
    private boolean isConfirmedHoliday() {
        String value = redisTemplate.opsForValue().get(HOLIDAY_KEY_PREFIX + LocalDate.now());
        return "confirmed".equals(value);
    }

    /** 某基金本地是否已有今天这根 bar */
    private boolean hasTodayBar(String fundCode) {
        return klineMapper.selectCount(new LambdaQueryWrapper<FundEtfKline>()
                .eq(FundEtfKline::getFundCode, fundCode)
                .eq(FundEtfKline::getTradeDate, LocalDate.now())) > 0;
    }

    /**
     * 记一次"疑似节假日"观察：第一次记 pending（短 TTL，当天内有效），
     * 已有 pending 又再次观察到 → 升级为 confirmed（持续到自然日结束），当天剩余时间全部跳过。
     */
    private void observeHoliday() {
        String key = HOLIDAY_KEY_PREFIX + LocalDate.now();
        String value = redisTemplate.opsForValue().get(key);
        if ("pending".equals(value)) {
            java.time.Duration tillMidnight = java.time.Duration.between(LocalDateTime.now(),
                    LocalDate.now().plusDays(1).atStartOfDay());
            redisTemplate.opsForValue().set(key, "confirmed", tillMidnight);
            LOGGER.info("连续两轮未见当天行情，判定今日为节假日（数据源自判），盘中同步今日不再发起");
            // 落进休市名单：既是留痕（source=observed，可人工核对/删除），也让后续判定不再依赖 Redis 是否过期
            tradingCalendarService.recordObservedHoliday(LocalDate.now(), "盘面自判：连续两轮成功请求均无当天行情");
        } else if (value == null) {
            redisTemplate.opsForValue().set(key, "pending", java.time.Duration.ofSeconds(HOLIDAY_PENDING_SECONDS));
        }
    }

    /**
     * 档案（规模/费率）强制刷新（每交易日 15:05）：收盘后立即再刷一次最新规模，
     * 并把当日规模快照落进 fund_scale_history（供行情图「基金规模」副图）。
     * 成功后 profile_sync_date 已是今天 → 15:30 日K全量覆盖的档案刷新会自然跳过，不会重复拉。
     */
    @Override
    public void refreshAllProfiles() {
        if (isMarketClosedToday()) {
            return;
        }
        lockUtils.runWithLock("job:profile:refresh", 60, () -> {
            List<FundBasic> funds = fundBasicMapper.selectList(new LambdaQueryWrapper<FundBasic>()
                    .eq(FundBasic::getStatus, 1));
            int ok = 0;
            List<String> errors = new ArrayList<>();
            for (FundBasic fund : funds) {
                try {
                    refreshProfileIfNeeded(fund, true);
                    refreshPremiumRate(fund);
                    ok++;
                } catch (Exception e) {
                    LOGGER.error("档案刷新[{}]失败", fund.getFundCode(), e);
                    errors.add(fund.getFundCode() + ":" + e.getMessage());
                }
            }
            LOGGER.info("15:05 档案（规模/费率）强制刷新完成：成功 {}/{} 只{}", ok, funds.size(),
                    errors.isEmpty() ? "" : "，失败: " + String.join(" | ", errors));
        });
    }

    private FundEtfKline toKlineEntity(String fundCode, EastmoneyClient.KlineItem item) {
        FundEtfKline row = new FundEtfKline();
        row.setFundCode(fundCode);
        row.setTradeDate(item.date());
        row.setOpen(item.open());
        row.setClose(item.close());
        row.setHigh(item.high());
        row.setLow(item.low());
        row.setVolume(item.volume());
        row.setAmount(item.amount());
        return row;
    }

    private void updateLastSync(FundBasic fund, LocalDate maxDate) {
        // last_sync_at 与数据日期解耦（V5.49）：每次同步动作成功即刷新，盘中数据日期不变也照刷，
        // 前端"最后同步"列的时分秒才是"最近一次同步发生在几点"
        fund.setLastSyncAt(LocalDateTime.now());
        if (maxDate != null && (fund.getLastSyncDate() == null || maxDate.isAfter(fund.getLastSyncDate()))) {
            fund.setLastSyncDate(maxDate);
        }
        fundBasicMapper.updateById(fund);
    }

    private void writeLog(SyncTypeEnum type, String fundCode, boolean ok, int count, String error) {
        writeLog(type, fundCode, ok, count, error, LocalDateTime.now());
    }

    private void writeLog(SyncTypeEnum type, String fundCode, boolean ok, int count, String error,
                          LocalDateTime startAt) {
        SyncLog log = new SyncLog();
        log.setFundCode(fundCode);
        log.setSyncType(type.name());
        log.setStatus(ok ? 1 : 0);
        log.setRecordCount(count);
        log.setErrorMsg(error == null || error.isBlank() ? null
                : error.substring(0, Math.min(error.length(), 500)));
        log.setStartTime(startAt);
        log.setEndTime(LocalDateTime.now());
        syncLogMapper.insert(log);
    }
}
