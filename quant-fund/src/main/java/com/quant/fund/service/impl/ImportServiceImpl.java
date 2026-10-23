package com.quant.fund.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.quant.common.exception.BizException;
import com.quant.common.util.JsonUtils;
import com.quant.common.util.LockUtils;
import com.quant.fund.client.EastmoneyClient;
import com.quant.fund.dto.BatchImportProgressVO;
import com.quant.fund.dto.EtfCandidateVO;
import com.quant.fund.dto.FundCheckVO;
import com.quant.fund.dto.OtcCandidateVO;
import com.quant.fund.dto.OtcCandidatesVO;
import com.quant.fund.dto.OtcScaleSnapshot;
import com.quant.fund.dto.TaskProgressVO;
import com.quant.fund.entity.FundBasic;
import com.quant.fund.entity.FundEtfKline;
import com.quant.fund.entity.FundNav;
import com.quant.fund.entity.IndexValuation;
import com.quant.fund.entity.SyncLog;
import com.quant.fund.enums.FundTypeEnum;
import com.quant.fund.enums.SyncTypeEnum;
import com.quant.fund.mapper.FundBasicMapper;
import com.quant.fund.mapper.FundEtfKlineMapper;
import com.quant.fund.mapper.FundNavMapper;
import com.quant.fund.mapper.IndexValuationMapper;
import com.quant.fund.mapper.SyncLogMapper;
import com.quant.fund.service.DividendService;
import com.quant.fund.service.ImportService;
import com.quant.fund.service.TaskProgressStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 基金数据导入服务实现（FR3）：校验 -> 异步导入 ETF日K(前复权+未复权)/场外净值(含复权净值)/分红记录/跟踪指数估值。
 * 说明：delete+批量插入 用 TransactionTemplate 保证原子（@Async 自调用不走代理）。
 * 覆盖语义（V5.26 用户口径）：导入对已存在的同类数据一律**整段覆盖**——日K/净值/估值按"先删后插"，
 * 分红由 DividendService.refresh 的"先删后插"覆盖，保证重导一次即与数据源对齐。
 *
 * <p>批量导入（V5.41）：串行复用单基金管线（东财按路径间歇封堵，并发只会更快触发封堵），
 * 与数据同步任务批量互斥（tryLockAll），封堵窗口自动暂停续跑，单只失败记入失败清单不中断，
 * 进度存 Redis（2h 过期）供前端轮询。批量任务经 taskExecutor 线程池执行（POST 立即返回）。
 */
@Service
public class ImportServiceImpl implements ImportService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImportServiceImpl.class);

    private static final String SOURCE_CSINDEX = "CSINDEX";

    private static final int NAV_PAGE_SIZE = 20;

    /** 批量进度 Redis 键前缀（2h 过期） */
    private static final String BATCH_PROGRESS_KEY = "task:batch:progress:";

    /** 候选清单 Redis 缓存键前缀（10min 过期，避免反复抓东财列表） */
    private static final String CANDIDATES_CACHE_KEY = "import:batch:candidates:";

    /** 批量导入与数据同步任务互斥的锁清单（持有期间对应定时任务会跳过并留日志） */
    private static final List<String> BATCH_LOCKS = List.of(
            "import:batch", "job:etf:daily", "job:nav", "job:valuation", "job:sync:watch");

    /** 单批代码上限：防误操作（粘贴错整份名单/文件）导致任务跑数小时 */
    private static final int BATCH_MAX_CODES = 200;

    /** 自动导入（V6.30）：单批等待上限（秒），超时保留剩余批次到下周，避免整轮任务挂死 */
    private static final int AUTO_IMPORT_BATCH_TIMEOUT_SECONDS = 3600;

    private final EastmoneyClient client;

    private final FundBasicMapper fundBasicMapper;

    private final FundEtfKlineMapper klineMapper;

    private final FundNavMapper navMapper;

    private final IndexValuationMapper valuationMapper;

    /** 分红记录（股息率与行情图除息点位 q 标记的数据源；V5.26 起纳入导入） */
    private final DividendService dividendService;

    private final SyncLogMapper syncLogMapper;

    private final TaskProgressStore progressStore;

    private final TransactionTemplate transactionTemplate;

    private final LockUtils lockUtils;

    private final StringRedisTemplate redisTemplate;

    /** 场外规模快照抓取在途标记（V6.28）：启动预热与页面查询并发时只让一个线程去抓 */
    private final AtomicBoolean otcScaleLoading = new AtomicBoolean(false);

    /** 批量任务执行线程池（POST 立即返回，任务体不能像单只导入那样占住请求线程） */
    private final ThreadPoolTaskExecutor taskExecutor;

    /** 批量任务判定封堵后的暂停时长（毫秒）；实测封堵窗口约 5 分钟一轮，默认 90 秒探一次 */
    @Value("${import.batch.pause-millis:90000}")
    private long batchPauseMillis;

    /** 批量任务相邻两只基金之间的间隔（毫秒）：串行降速是防封堵的核心手段 */
    @Value("${import.batch.interval-millis:500}")
    private long batchIntervalMillis;

    public ImportServiceImpl(EastmoneyClient client, FundBasicMapper fundBasicMapper, FundEtfKlineMapper klineMapper,
                             FundNavMapper navMapper, IndexValuationMapper valuationMapper,
                             DividendService dividendService, SyncLogMapper syncLogMapper,
                             TaskProgressStore progressStore, TransactionTemplate transactionTemplate,
                             LockUtils lockUtils, StringRedisTemplate redisTemplate,
                             @Qualifier("taskExecutor") ThreadPoolTaskExecutor taskExecutor) {
        this.client = client;
        this.fundBasicMapper = fundBasicMapper;
        this.klineMapper = klineMapper;
        this.navMapper = navMapper;
        this.valuationMapper = valuationMapper;
        this.dividendService = dividendService;
        this.syncLogMapper = syncLogMapper;
        this.progressStore = progressStore;
        this.transactionTemplate = transactionTemplate;
        this.lockUtils = lockUtils;
        this.redisTemplate = redisTemplate;
        this.taskExecutor = taskExecutor;
    }

    @Override
    public FundCheckVO check(String fundCode) {
        EastmoneyClient.FundProfile profile = client.fetchFundProfile(fundCode);
        // 池内状态按"事实"拆开（V4.1）：移出自选是软删（status=0，历史数据保留），
        // 所以"库里有这行"≠"在自选池"——原来只看行是否存在，会把用户删过的基金说成"已存在"。
        FundBasic existing = fundBasicMapper.selectOne(new LambdaQueryWrapper<FundBasic>()
                .eq(FundBasic::getFundCode, fundCode));
        boolean inPool = existing != null && Integer.valueOf(1).equals(existing.getStatus());
        boolean removedFromPool = existing != null && !inPool;
        LocalDate lastSyncDate = existing == null ? null : existing.getLastSyncDate();
        if (profile.fundType() == null || !profile.fundType().contains("指数")) {
            return new FundCheckVO(fundCode, profile.name(), null, null, profile.fundType(), profile.company(),
                    null, null, profile.estabDate(), false,
                    "非指数型基金（类型：" + profile.fundType() + "），平台仅支持指数基金",
                    inPool, removedFromPool, lastSyncDate,
                    profile.fundScale(), profile.fundScaleDate(),
                    profile.mgmtFeeRate(), profile.custFeeRate(), profile.salesFeeRate());
        }
        // 有场内行情 → ETF；否则为场外指数基金
        Integer market = detectMarket(fundCode);
        Integer fundType = market != null ? FundTypeEnum.ETF.getCode() : FundTypeEnum.OTC.getCode();
        String marketCode = market == null ? null : (market == 1 ? "SH" : "SZ");
        // 规模/费率随档案透传（V6.42）：check 本就拉了 f10 档案，零额外请求——导入落库时即可带上
        return new FundCheckVO(fundCode, profile.name(), fundType, marketCode, profile.fundType(), profile.company(),
                profile.indexCode(), profile.indexName(), profile.estabDate(), true, null,
                inPool, removedFromPool, lastSyncDate,
                profile.fundScale(), profile.fundScaleDate(),
                profile.mgmtFeeRate(), profile.custFeeRate(), profile.salesFeeRate());
    }

    @Override
    public String importFund(String fundCode) {
        FundCheckVO check = check(fundCode);
        if (!check.supported()) {
            throw new BizException(check.reason());
        }
        String taskId = UUID.randomUUID().toString().replace("-", "");
        progressStore.save(new TaskProgressVO(taskId, TaskProgressVO.RUNNING, "校验通过，开始导入", 0, 0, null));
        try {
            doImport(taskId, check);
        } catch (BizException e) {
            // 单只导入沿用原口径：失败不向上抛（前端轮询进度可见 FAILED 与原因）；批量导入才用异常驱动失败清单
        }
        return taskId;
    }

    @Override
    public TaskProgressVO progress(String taskId) {
        TaskProgressVO progress = progressStore.get(taskId);
        if (progress == null) {
            throw new BizException("任务不存在或已过期");
        }
        return progress;
    }

    // ===== 批量导入（V5.41）=====

    @Override
    public List<EtfCandidateVO> etfCandidates(BigDecimal minScaleYi, int minYears) {
        if (minScaleYi == null || minScaleYi.compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException("规模下限须 ≥ 0");
        }
        if (minYears < 0 || minYears > 30) {
            throw new BizException("上市年限须在 0~30 之间");
        }
        String cacheKey = CANDIDATES_CACHE_KEY + minScaleYi.stripTrailingZeros().toPlainString() + ":" + minYears;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return JsonUtils.mapper().readValue(cached,
                        JsonUtils.mapper().getTypeFactory().constructCollectionType(List.class, EtfCandidateVO.class));
            } catch (RuntimeException e) {
                // 缓存坏了不值得让页面报错：删掉缓存走一次实时抓取
                LOGGER.error("候选清单缓存反序列化失败，删除缓存后实时抓取", e);
                redisTemplate.delete(cacheKey);
            }
        }
        Set<String> poolCodes = poolCodes();
        LocalDate listedBefore = LocalDate.now().minusYears(minYears);
        BigDecimal capYuanMin = minScaleYi.multiply(BigDecimal.valueOf(1e8));
        List<EtfCandidateVO> result = new ArrayList<>();
        for (EastmoneyClient.EtfBoardItem item : client.fetchEtfBoardList()) {
            if (BigDecimal.valueOf(item.capYuan()).compareTo(capYuanMin) <= 0) {
                continue;
            }
            LocalDate listed = LocalDate.of(item.listedDate() / 10000, item.listedDate() / 100 % 100, item.listedDate() % 100);
            if (listed.isAfter(listedBefore)) {
                continue;
            }
            result.add(new EtfCandidateVO(item.code(), item.name(),
                    BigDecimal.valueOf(item.capYuan()).divide(BigDecimal.valueOf(1e8), 1, RoundingMode.HALF_UP),
                    listed.toString(), poolCodes.contains(item.code())));
        }
        // 结果缓存 10 分钟：候选列表是浏览性质的数据，反复查询没必要每次都抓 17 页
        redisTemplate.opsForValue().set(cacheKey, JsonUtils.toJson(result), Duration.ofMinutes(10));
        return result;
    }

    /** 场外候选缓存键前缀（V6.27） */
    private static final String OTC_CANDIDATES_CACHE_KEY = "import:batch:otc-candidates:";

    /** 场外规模快照缓存键（V6.28）：季报数据一年只变 4 次，缓存 7 天 */
    private static final String OTC_SCALE_CACHE_KEY = "import:otc-scale:latest";

    /** 场外规模快照缓存时长（V6.28） */
    private static final Duration OTC_SCALE_TTL = Duration.ofDays(7);

    @Override
    public OtcCandidatesVO otcCandidates(BigDecimal minScaleYi, int minYears) {
        if (minScaleYi == null || minScaleYi.compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException("规模下限须 ≥ 0");
        }
        if (minYears < 0 || minYears > 30) {
            throw new BizException("成立年限须在 0~30 之间");
        }
        OtcScaleSnapshot scale = scaleSnapshot();
        String cacheKey = OTC_CANDIDATES_CACHE_KEY + minScaleYi.stripTrailingZeros().toPlainString() + ":"
                + minYears + (scale == null ? ":noscale" : "");
        // 只在规模快照就绪时写缓存：快照加载中的结果（未按规模过滤）不缓存，避免用户看到"假过滤"
        if (scale != null) {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                try {
                    return JsonUtils.fromJson(cached, OtcCandidatesVO.class);
                } catch (RuntimeException e) {
                    LOGGER.error("场外候选清单缓存反序列化失败，删除缓存后实时抓取", e);
                    redisTemplate.delete(cacheKey);
                }
            }
        }
        Set<String> poolCodes = poolCodes();
        LocalDate estabBefore = LocalDate.now().minusYears(minYears);
        // 榜单默认按近1年涨幅降序返回（sc=1nzf&st=desc），保持原序即"涨幅从高到低"
        List<OtcCandidateVO> result = client.fetchOtcIndexFundRank().stream()
                .filter(item -> !item.estabDate().isBlank()
                        && LocalDate.parse(item.estabDate()).isBefore(estabBefore))
                .map(item -> new OtcCandidateVO(item.code(), item.name(), item.estabDate(), item.pct1y(),
                        scaleYi(scale, item.code()), poolCodes.contains(item.code())))
                // 规模快照未就绪时不按规模过滤（页面提示"规模数据加载中"），就绪后按季报口径净资产过滤
                .filter(item -> scale == null || minScaleYi.compareTo(BigDecimal.ZERO) == 0
                        || (item.scaleYi() != null && item.scaleYi().compareTo(minScaleYi) >= 0))
                .toList();
        OtcCandidatesVO vo = new OtcCandidatesVO(scale != null,
                scale == null ? null : scale.reportDate(), result);
        if (scale != null) {
            redisTemplate.opsForValue().set(cacheKey, JsonUtils.toJson(vo), Duration.ofMinutes(10));
        }
        return vo;
    }

    /** 规模取值（亿元，保留 2 位）：快照缺失或该基金无数据时返回 null（前端显示 --） */
    private static BigDecimal scaleYi(OtcScaleSnapshot scale, String code) {
        if (scale == null) {
            return null;
        }
        BigDecimal raw = scale.scaleYi().get(code);
        return raw == null ? null : raw.setScale(2, RoundingMode.HALF_UP);
    }

    // ===== 场外规模快照（V6.28）=====

    /**
     * 最近一个**已完整披露**的季报截止日（V6.28）：按法定披露截止日推导——一季报 4/30、半年报 8/31、
     * 三季报 10/31、年报次年 3/31。例：2026-10-12 落在三季报截止（10/31）之前，取 2026-06-30，
     * 与东财实际返回的数据口径一致；东财若提前发布新季报，本标签在下一个披露窗口自动跟上。
     */
    static LocalDate latestReportQuarter(LocalDate today) {
        LocalDate latest = LocalDate.of(today.getYear() - 1, 12, 31);
        LocalDate[] ends = {LocalDate.of(today.getYear(), 3, 31), LocalDate.of(today.getYear(), 6, 30),
                LocalDate.of(today.getYear(), 9, 30), LocalDate.of(today.getYear(), 12, 31)};
        LocalDate[] deadlines = {LocalDate.of(today.getYear(), 4, 30), LocalDate.of(today.getYear(), 8, 31),
                LocalDate.of(today.getYear(), 10, 31), LocalDate.of(today.getYear() + 1, 3, 31)};
        for (int i = 0; i < ends.length; i++) {
            if (!today.isBefore(deadlines[i])) {
                latest = ends[i];
            }
        }
        return latest;
    }

    /**
     * 场外规模快照：Redis 缓存 7 天（季报数据、一年只变 4 次）。缓存未命中时**当前线程**同步抓取
     * （约 2 分钟，受全局 300ms 限频约束）；抓取期间其他请求直接拿到 null（页面提示"规模数据加载中"、
     * 本次不按规模过滤），不让请求线程排队干等。应用启动后由 {@link #warmOtcScaleCache()} 预热，
     * 正常情况下用户查询时缓存已就绪。
     */
    private OtcScaleSnapshot scaleSnapshot() {
        String cached = redisTemplate.opsForValue().get(OTC_SCALE_CACHE_KEY);
        if (cached != null) {
            try {
                return JsonUtils.fromJson(cached, OtcScaleSnapshot.class);
            } catch (RuntimeException e) {
                LOGGER.error("场外规模缓存反序列化失败，删除缓存后重新抓取", e);
                redisTemplate.delete(OTC_SCALE_CACHE_KEY);
            }
        }
        if (!otcScaleLoading.compareAndSet(false, true)) {
            return null;
        }
        try {
            Map<String, BigDecimal> scale = client.fetchIndexFundScale();
            OtcScaleSnapshot snapshot = new OtcScaleSnapshot(latestReportQuarter(LocalDate.now()).toString(), scale);
            redisTemplate.opsForValue().set(OTC_SCALE_CACHE_KEY, JsonUtils.toJson(snapshot), OTC_SCALE_TTL);
            LOGGER.info("场外规模快照抓取完成：{} 只（季报截止 {}），缓存 7 天", scale.size(), snapshot.reportDate());
            return snapshot;
        } catch (RuntimeException e) {
            LOGGER.error("场外规模快照抓取失败（页面会提示加载中，稍后查询自动重试）", e);
            return null;
        } finally {
            otcScaleLoading.set(false);
        }
    }

    /**
     * 应用启动后预热场外规模快照（V6.28）：仅当缓存缺失/过期时发起（后台线程，失败不阻断启动、
     * 由 scaleSnapshot 自身记录日志），让用户打开数据导入页时即点即得；缓存 7 天内不重复抓取。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warmOtcScaleCache() {
        if (Boolean.TRUE.equals(redisTemplate.hasKey(OTC_SCALE_CACHE_KEY))) {
            return;
        }
        LOGGER.info("场外规模快照缓存缺失，启动后台预热（约 2 分钟，不影响其他功能）");
        taskExecutor.execute(this::scaleSnapshot);
    }

    @Override
    public BatchStartResult startBatch(List<String> codes) {
        // 清洗：只留 6 位数字代码、保序去重（ETF 与场外代码都是 6 位）
        LinkedHashSet<String> distinct = new LinkedHashSet<>();
        if (codes != null) {
            for (String raw : codes) {
                String code = raw == null ? "" : raw.trim();
                if (code.matches("\\d{6}")) {
                    distinct.add(code);
                }
            }
        }
        if (distinct.isEmpty()) {
            throw new BizException("没有可导入的基金代码（须为 6 位数字）");
        }
        if (distinct.size() > BATCH_MAX_CODES) {
            throw new BizException("单批最多 " + BATCH_MAX_CODES + " 只（当前 " + distinct.size() + " 只），请分批导入");
        }
        // 已在池中的直接剔除并回显（铁律 11：让前端拿到真实的接收/跳过状态）
        Set<String> poolCodes = poolCodes();
        List<String> skippedExisting = new ArrayList<>();
        List<String> accepted = new ArrayList<>();
        for (String code : distinct) {
            if (poolCodes.contains(code)) {
                skippedExisting.add(code);
            } else {
                accepted.add(code);
            }
        }
        if (accepted.isEmpty()) {
            throw new BizException("所选基金均已在自选池中，无需导入");
        }
        String taskId = UUID.randomUUID().toString().replace("-", "");
        saveBatch(new BatchImportProgressVO(taskId, BatchImportProgressVO.RUNNING,
                "已排队（待导入 " + accepted.size() + " 只）", accepted.size(), 0, 0, 0,
                null, null, List.of(), LocalDateTime.now(), null));
        // 经线程池执行（POST 立即返回）；任务体内部与同步任务批量互斥
        taskExecutor.execute(() -> runBatch(taskId, accepted));
        return new BatchStartResult(taskId, accepted, skippedExisting);
    }

    @Override
    public BatchImportProgressVO batchProgress(String taskId) {
        String json = redisTemplate.opsForValue().get(BATCH_PROGRESS_KEY + taskId);
        if (json == null) {
            throw new BizException("批量任务不存在或已过期（进度保留 2 小时）");
        }
        return JsonUtils.fromJson(json, BatchImportProgressVO.class);
    }

    // ===== 自动导入新基金（V6.30，周三 13:01 定时任务与手动预演共用）=====

    @Override
    public AutoImportReport autoImportEligibleFunds(BigDecimal minScaleYi, int minYears, boolean dryRun) {
        List<String> notes = new ArrayList<>();
        // 场外候选依赖"最新季报规模快照"：未就绪时本轮跳过场外，绝不按"无规模过滤"静默放行（会导入小基金）
        OtcScaleSnapshot scale = scaleSnapshot();
        List<String> etfNew = new ArrayList<>();
        List<String> otcNew = new ArrayList<>();
        // V6.53 用户口径：自动导入只收**系统里从未出现过**的基金——曾经导入过又被删除的（软删 status=0，
        // 历史数据保留）不再被周三任务自动加回；手动导入不受影响（数据导入菜单仍可重新导入，check() 会提示
        // "曾导入、已移出自选"，导入即恢复）。这里按"fund_basic 是否已有该行"判断（不看 status）。
        Set<String> existed = existingCodes();
        int etfRemoved = 0;
        int otcRemoved = 0;
        try {
            for (EtfCandidateVO candidate : etfCandidates(minScaleYi, minYears)) {
                if (candidate.inPool()) {
                    continue;
                }
                if (existed.contains(candidate.fundCode())) {
                    etfRemoved++;
                    continue;
                }
                etfNew.add(candidate.fundCode());
            }
        } catch (RuntimeException e) {
            LOGGER.error("自动导入：场内候选获取失败（本轮跳过场内部分，下周再试）", e);
            notes.add("场内候选获取失败：" + e.getMessage());
        }
        if (scale == null) {
            LOGGER.warn("自动导入：场外规模快照未就绪，本轮跳过场外部分（下旬任务自动重试）");
            notes.add("场外规模快照未就绪，已跳过场外部分");
        } else {
            try {
                for (OtcCandidateVO candidate : otcCandidates(minScaleYi, minYears).items()) {
                    if (candidate.inPool()) {
                        continue;
                    }
                    if (existed.contains(candidate.fundCode())) {
                        otcRemoved++;
                        continue;
                    }
                    otcNew.add(candidate.fundCode());
                }
            } catch (RuntimeException e) {
                LOGGER.error("自动导入：场外候选获取失败（本轮跳过场外部分，下周再试）", e);
                notes.add("场外候选获取失败：" + e.getMessage());
            }
        }
        if (etfRemoved + otcRemoved > 0) {
            LOGGER.info("自动导入：跳过 {} 只曾导入过又被删除的基金（场内 {}、场外 {}），不自动加回",
                    etfRemoved + otcRemoved, etfRemoved, otcRemoved);
            notes.add("跳过 " + (etfRemoved + otcRemoved) + " 只曾被删除的基金");
        }
        LinkedHashSet<String> merged = new LinkedHashSet<>(etfNew);
        merged.addAll(otcNew);
        List<String> codes = new ArrayList<>(merged);
        if (dryRun) {
            LOGGER.info("自动导入预演（dryRun）：场内新增 {} 只、场外新增 {} 只，合计 {} 只",
                    etfNew.size(), otcNew.size(), codes.size());
            return new AutoImportReport(true, etfNew, otcNew, List.of(), String.join("；", notes));
        }
        if (codes.isEmpty()) {
            LOGGER.info("自动导入：没有符合条件且不在池中的新基金");
            return new AutoImportReport(false, etfNew, otcNew, List.of(), "没有需要导入的新基金");
        }
        // 分批串行（单批上限 200）：等上一批跑完再提交下一批——批与批之间共用互斥锁与数据源，并发只会互相撞
        List<String> imported = new ArrayList<>();
        int batches = (codes.size() + BATCH_MAX_CODES - 1) / BATCH_MAX_CODES;
        for (int i = 0; i < codes.size(); i += BATCH_MAX_CODES) {
            List<String> chunk = codes.subList(i, Math.min(codes.size(), i + BATCH_MAX_CODES));
            int batchNo = i / BATCH_MAX_CODES + 1;
            BatchStartResult started = startBatch(chunk);
            imported.addAll(started.accepted());
            LOGGER.info("自动导入第 {}/{} 批已提交：接收 {} 只（已在池跳过 {} 只）",
                    batchNo, batches, started.accepted().size(), started.skippedExisting().size());
            if (!waitBatchDone(started.taskId(), AUTO_IMPORT_BATCH_TIMEOUT_SECONDS)) {
                notes.add("第 " + batchNo + "/" + batches + " 批等待超时，剩余批次留待下周");
                break;
            }
        }
        LOGGER.info("自动导入完成：符合条件 {} 只（场内 {} + 场外 {}），提交导入 {} 只、分 {} 批{}",
                codes.size(), etfNew.size(), otcNew.size(), imported.size(), batches,
                notes.isEmpty() ? "" : "；" + String.join("；", notes));
        return new AutoImportReport(false, etfNew, otcNew, imported, String.join("；", notes));
    }

    /**
     * 等待批量导入任务结束（10 秒轮询一次 Redis 进度，上限由调用方给定）。
     * 进度键 2 小时过期：取不到进度视为"已结束或过期"直接放行，避免整轮自动导入卡死。
     */
    private boolean waitBatchDone(String taskId, int timeoutSeconds) {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            BatchImportProgressVO progress;
            try {
                progress = batchProgress(taskId);
            } catch (RuntimeException e) {
                LOGGER.warn("自动导入批次[{}]进度取不到（任务已结束或已过期）：{}", taskId, e.getMessage());
                return true;
            }
            if (!BatchImportProgressVO.RUNNING.equals(progress.status())) {
                LOGGER.info("自动导入批次[{}]结束：{}（成功 {} / 失败 {}）",
                        taskId, progress.message(), progress.success(), progress.failed());
                return true;
            }
            try {
                Thread.sleep(10_000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        LOGGER.warn("自动导入批次[{}]等待超过 {} 秒仍未完成", taskId, timeoutSeconds);
        return false;
    }

    /**
     * 批量任务体：串行逐只导入。与数据同步任务批量互斥（持有 etf:daily/nav/valuation/sync:watch 四把锁，
     * 持有期间对应定时任务会自动跳过并留日志）；东财封堵自适应——连续 3 只失败判定进入封堵窗口，
     * 暂停 batchPauseMillis 后继续；单只失败只记失败清单，不中断批量。
     */
    private void runBatch(String taskId, List<String> codes) {
        LockUtils.MultiLock locks = acquireBatchLocks(taskId);
        if (locks == null) {
            return;
        }
        try {
            batchLoop(taskId, codes);
        } finally {
            locks.close();
        }
    }

    /**
     * 获取批量互斥锁：盘中每 5 分钟的同步任务会短暂持有 job:sync:watch（几秒），直接 tryLock 很容易撞上，
     * 故带 6 次 × 10 秒的等待重试（约 1 分钟），仍拿不到才判失败。
     */
    private LockUtils.MultiLock acquireBatchLocks(String taskId) {
        for (int attempt = 1; attempt <= 6; attempt++) {
            LockUtils.MultiLock locks = lockUtils.tryLockAll(BATCH_LOCKS, 120);
            if (locks != null) {
                LOGGER.info("批量导入[{}]获取互斥锁成功（第 {} 次尝试）", taskId, attempt);
                return locks;
            }
            if (attempt < 6) {
                saveBatch(new BatchImportProgressVO(taskId, BatchImportProgressVO.RUNNING,
                        "有同步任务正在执行，" + 10 + " 秒后重试获取互斥锁（第 " + attempt + "/6 次）",
                        0, 0, 0, 0, null, null, List.of(), LocalDateTime.now(), null));
                try {
                    Thread.sleep(10_000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
        saveBatch(new BatchImportProgressVO(taskId, BatchImportProgressVO.FAILED,
                "有同步/导入任务正在执行（如 15:30 全量同步、盘中同步），已等待约 1 分钟仍未让出，请稍后重新发起",
                0, 0, 0, 0, null, null, List.of(), LocalDateTime.now(), LocalDateTime.now()));
        return null;
    }

    /** 批量任务主循环（调用方持有互斥锁） */
    private void batchLoop(String taskId, List<String> codes) {
        LOGGER.info("批量导入[{}]开始：共 {} 只，与数据同步任务互斥", taskId, codes.size());
        int success = 0;
        int failed = 0;
        int consecutiveFailures = 0;
        List<BatchImportProgressVO.FailItem> failures = new ArrayList<>();
        LocalDateTime startedAt = LocalDateTime.now();
        for (int i = 0; i < codes.size(); i++) {
            String code = codes.get(i);
            saveBatch(new BatchImportProgressVO(taskId, BatchImportProgressVO.RUNNING,
                    "正在导入 " + (i + 1) + "/" + codes.size(), codes.size(), i, success, failed,
                    code, null, List.copyOf(failures), startedAt, null));
            try {
                doImport(taskId + ":" + code, check(code));
                success++;
                consecutiveFailures = 0;
            } catch (Exception e) {
                failed++;
                consecutiveFailures++;
                String reason = e.getMessage() == null ? "导入失败" : e.getMessage();
                failures.add(new BatchImportProgressVO.FailItem(code, null, reason));
                LOGGER.error("批量导入[{}]基金[{}]失败（{}/{}）", taskId, code, i + 1, codes.size(), e);
            }
            saveBatch(new BatchImportProgressVO(taskId, BatchImportProgressVO.RUNNING,
                    "已处理 " + (i + 1) + "/" + codes.size(), codes.size(), i + 1, success, failed,
                    null, null, List.copyOf(failures), startedAt, null));
            // 封堵自适应：连续 3 只失败（每只内部已重试 3 次）大概率进入封堵窗口，暂停探窗
            if (consecutiveFailures >= 3 && i < codes.size() - 1) {
                LOGGER.warn("批量导入[{}]连续 {} 只失败，判定进入数据源封堵窗口，暂停 {}ms 后继续", taskId, consecutiveFailures, batchPauseMillis);
                saveBatch(new BatchImportProgressVO(taskId, BatchImportProgressVO.RUNNING,
                        "疑似数据源封堵窗口，暂停 " + (batchPauseMillis / 1000) + " 秒后自动继续",
                        codes.size(), i + 1, success, failed, null, null, List.copyOf(failures), startedAt, null));
                if (!sleepQuietly(batchPauseMillis)) {
                    interrupted(taskId, codes.size(), i + 1, success, failed, failures);
                    return;
                }
                consecutiveFailures = 0;
            } else if (i < codes.size() - 1) {
                if (!sleepQuietly(batchIntervalMillis)) {
                    interrupted(taskId, codes.size(), i + 1, success, failed, failures);
                    return;
                }
            }
        }
        String summary = "批量导入完成：成功 " + success + " 只" + (failed > 0 ? "，失败 " + failed + " 只（见失败清单）" : "");
        LOGGER.info("批量导入[{}]结束：{}", taskId, summary);
        saveBatch(new BatchImportProgressVO(taskId, BatchImportProgressVO.DONE, summary,
                codes.size(), codes.size(), success, failed, null, null, List.copyOf(failures), startedAt, LocalDateTime.now()));
    }

    /** 可中断的等待：正常睡完返回 true；被中断时恢复中断位、记"人工终止"进度并返回 false */
    private boolean sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void interrupted(String taskId, int total, int done, int success, int failed,
                             List<BatchImportProgressVO.FailItem> failures) {
        saveBatch(new BatchImportProgressVO(taskId, BatchImportProgressVO.DONE,
                "任务被中断（已完成 " + success + " 只，失败 " + failed + " 只）",
                total, done, success, failed, null, null, List.copyOf(failures), LocalDateTime.now(), LocalDateTime.now()));
    }

    private void saveBatch(BatchImportProgressVO progress) {
        redisTemplate.opsForValue().set(BATCH_PROGRESS_KEY + progress.taskId(), JsonUtils.toJson(progress),
                Duration.ofHours(2));
    }

    /**
     * 系统里"曾经出现过"的全部基金代码（V6.53）：含在池（status=1）与已删除（status=0，软删保留历史数据）。
     * 自动导入用它排除"曾导入又被删除"的基金；手动导入不查这个集合（允许重新导入）。
     */
    private Set<String> existingCodes() {
        Set<String> codes = new HashSet<>();
        fundBasicMapper.selectList(new LambdaQueryWrapper<FundBasic>()
                        .select(FundBasic::getFundCode))
                .forEach(fund -> codes.add(fund.getFundCode()));
        return codes;
    }

    /** 当前自选池内全部基金代码（status=1） */
    private Set<String> poolCodes() {
        Set<String> codes = new HashSet<>();
        fundBasicMapper.selectList(new LambdaQueryWrapper<FundBasic>().eq(FundBasic::getStatus, 1))
                .forEach(fund -> codes.add(fund.getFundCode()));
        return codes;
    }

    /**
     * 单基金导入管线（同步执行）。历史上标过 @Async("taskExecutor")，但 self-invocation 不走代理、
     * 实际一直在请求线程内同步执行，故 V5.41 起去掉该注解如实标注；批量导入按只调用本方法。
     * 失败抛异常（含原因）由调用方决定降级/记失败清单；进度仍按 taskId 写 Redis（无人轮询亦无副作用）。
     */
    private void doImport(String taskId, FundCheckVO check) {
        // 防御：调用方（批量循环）可能未先判 supported，货币型/非指数基金此处 fundType 为 null，必须先拦
        if (!check.supported()) {
            throw new BizException(check.reason());
        }
        LocalDateTime startAt = LocalDateTime.now();
        String code = check.code();
        try {
            LocalDate beg = historyBegin(check.estabDate());
            LocalDate today = LocalDate.now();
            LocalDate maxDate;
            if (check.fundType() == FundTypeEnum.ETF.getCode()) {
                maxDate = importEtfKline(taskId, check, beg, today);
            } else {
                maxDate = importOtcNav(taskId, code, beg, today);
            }
            // 分红记录（V5.26 纳入导入）：股息率(TTM/单次)与行情图除息点位 q 标记的数据源。
            // refresh 自带"先删后插"的覆盖语义；失败只降级不炸导入（装饰性数据，下次同步自动重试）。
            importDividends(taskId, code);
            if (check.indexCode() != null) {
                progressStore.save(new TaskProgressVO(taskId, TaskProgressVO.RUNNING,
                        "同步跟踪指数估值: " + check.indexName(), 0, 0, null));
                // 估值同为装饰性数据：失败/数据源无该指数估值都不应让导入整体失败（V5.26 前会炸掉整个导入）
                try {
                    importValuation(check.indexCode(), historyBegin(null));
                } catch (Exception e) {
                    LOGGER.error("指数[{}]估值导入失败（不影响本次导入，20:30 任务会自动补）", check.indexCode(), e);
                    progressStore.save(new TaskProgressVO(taskId, TaskProgressVO.RUNNING,
                            "指数估值暂不可用（不影响行情数据）", 0, 0, null));
                }
            }
            upsertFundBasic(check, maxDate);
            writeLog(SyncTypeEnum.HISTORY, code, true, 0, null, startAt);
            progressStore.save(new TaskProgressVO(taskId, TaskProgressVO.DONE, "导入完成", 0, 0, null));
            LOGGER.info("基金[{}]历史导入完成，数据截止 {}", code, maxDate);
        } catch (Exception e) {
            LOGGER.error("基金[{}]历史导入失败", code, e);
            writeLog(SyncTypeEnum.HISTORY, code, false, 0, e.getMessage(), startAt);
            progressStore.save(new TaskProgressVO(taskId, TaskProgressVO.FAILED, "导入失败", 0, 0, e.getMessage()));
            // 批量导入按只捕获此异常记入失败清单；单只导入由 importFund 透出给前端
            throw e instanceof BizException biz ? biz
                    : new BizException(e.getMessage() == null ? "导入失败" : e.getMessage());
        }
    }

    /**
     * 导入时同步分红记录（V5.26）：股息率与除息点位在导入完成时即可用，不必等下一次档案刷新。
     * DividendService.refresh 内部为"先删后插"覆盖；抓取失败（无分红/封堵）只记进度与日志。
     */
    private void importDividends(String taskId, String code) {
        progressStore.save(new TaskProgressVO(taskId, TaskProgressVO.RUNNING, "同步分红记录（股息率/除息点位）", 0, 0, null));
        try {
            int count = dividendService.refresh(code);
            LOGGER.info("基金[{}]分红记录导入 {} 条", code, count);
        } catch (Exception e) {
            LOGGER.error("基金[{}]分红记录导入失败（不影响行情数据，下次同步自动重试）", code, e);
            progressStore.save(new TaskProgressVO(taskId, TaskProgressVO.RUNNING,
                    "分红记录暂不可用（不影响行情数据）", 0, 0, null));
        }
    }

    private LocalDate importEtfKline(String taskId, FundCheckVO check, LocalDate beg, LocalDate today) {
        int market = "SH".equals(check.market()) ? 1 : 0;
        progressStore.save(new TaskProgressVO(taskId, TaskProgressVO.RUNNING, "拉取ETF日K(前复权)", 0, 0, null));
        List<EastmoneyClient.KlineItem> klines = client.fetchEtfKline(market, check.code(), beg, today);
        List<FundEtfKline> entities = klines.stream().map(item -> {
            FundEtfKline row = new FundEtfKline();
            row.setFundCode(check.code());
            row.setTradeDate(item.date());
            row.setOpen(item.open());
            row.setClose(item.close());
            row.setHigh(item.high());
            row.setLow(item.low());
            row.setVolume(item.volume());
            row.setAmount(item.amount());
            return row;
        }).toList();
        // 未复权收盘价（V5.26 纳入导入）：历史股息率的分母。导入时就补齐，股息率立即可看，
        // 不必等 15:30 全量覆盖任务；拉取失败只降级（前端按"--"处理），不影响日K导入。
        progressStore.save(new TaskProgressVO(taskId, TaskProgressVO.RUNNING, "补齐未复权收盘价（股息率分母）", 0, 0, null));
        fillUnadjustedClose(check.code(), market, beg, entities);
        progressStore.save(new TaskProgressVO(taskId, TaskProgressVO.RUNNING, "写入日K",
                entities.size(), entities.size(), null));
        transactionTemplate.executeWithoutResult(status -> {
            klineMapper.delete(new LambdaQueryWrapper<FundEtfKline>().eq(FundEtfKline::getFundCode, check.code()));
            Db.saveBatch(entities, 500);
        });
        return entities.isEmpty() ? null : entities.get(entities.size() - 1).getTradeDate();
    }

    /**
     * 批量补齐未复权收盘价（与 SyncServiceImpl.fillUnadjustedClose 同口径）：前复权价把分红从价格里抹掉，
     * 直接当股息率分母会系统性高估历史股息率，故额外拉一次 fqt=0 按交易日对齐写入。
     * 失败只记日志留空（前端按缺失"--"处理），**绝不能让装饰性数据把导入带崩**。
     */
    private void fillUnadjustedClose(String fundCode, int market, LocalDate beg, List<FundEtfKline> entities) {
        try {
            java.util.Map<LocalDate, BigDecimal> unadjusted = client
                    .fetchEtfKlineUnadjusted(market, fundCode, beg, LocalDate.now())
                    .stream()
                    .collect(java.util.stream.Collectors.toMap(EastmoneyClient.KlineItem::date,
                            EastmoneyClient.KlineItem::close, (first, second) -> first));
            if (unadjusted.isEmpty()) {
                return;
            }
            entities.forEach(entity -> entity.setUnadjClose(unadjusted.get(entity.getTradeDate())));
        } catch (Exception e) {
            LOGGER.error("基金[{}]未复权价拉取失败（历史股息率暂不可用，下次同步重试）", fundCode, e);
        }
    }

    private LocalDate importOtcNav(String taskId, String code, LocalDate beg, LocalDate today) {
        progressStore.save(new TaskProgressVO(taskId, TaskProgressVO.RUNNING, "拉取场外历史净值", 0, 0, null));
        List<EastmoneyClient.NavItem> items = fetchNavRange(code, beg, today);
        items.sort(Comparator.comparing(EastmoneyClient.NavItem::date));
        progressStore.save(new TaskProgressVO(taskId, TaskProgressVO.RUNNING, "计算复权净值并写入",
                items.size(), items.size(), null));
        List<FundNav> entities = buildNavWithAdj(code, items, null);
        transactionTemplate.executeWithoutResult(status -> {
            navMapper.delete(new LambdaQueryWrapper<FundNav>().eq(FundNav::getFundCode, code));
            Db.saveBatch(entities, 500);
        });
        return entities.isEmpty() ? null : entities.get(entities.size() - 1).getNavDate();
    }

    private void importValuation(String indexCode, LocalDate beg) {
        List<EastmoneyClient.ValuationItem> items = client.fetchIndexValuation(indexCode, beg, LocalDate.now());
        List<IndexValuation> entities = items.stream().filter(item -> item.pe() != null).map(item -> {
            IndexValuation row = new IndexValuation();
            row.setIndexCode(indexCode);
            row.setTradeDate(item.date());
            row.setPe(item.pe());
            row.setSource(SOURCE_CSINDEX);
            return row;
        }).toList();
        if (entities.isEmpty()) {
            LOGGER.warn("指数[{}]未获取到估值数据", indexCode);
            return;
        }
        transactionTemplate.executeWithoutResult(status -> {
            valuationMapper.delete(new LambdaQueryWrapper<IndexValuation>()
                    .eq(IndexValuation::getIndexCode, indexCode));
            Db.saveBatch(entities, 500);
        });
        LOGGER.info("指数[{}]估值导入 {} 条", indexCode, entities.size());
    }

    /** 拉取区间内全部净值（自动翻页） */
    private List<EastmoneyClient.NavItem> fetchNavRange(String code, LocalDate beg, LocalDate end) {
        List<EastmoneyClient.NavItem> all = new ArrayList<>();
        int pageIndex = 1;
        while (pageIndex <= 200) {
            EastmoneyClient.NavPage page = client.fetchOtcNavPage(code, pageIndex, NAV_PAGE_SIZE, beg, end);
            all.addAll(page.items());
            if (!page.hasNext() || page.items().isEmpty()) {
                break;
            }
            pageIndex++;
        }
        return all;
    }

    /**
     * 复权净值链式计算（技术文档 6.1）：
     * div_i = acc_i - acc_{i-1} - (unit_i - unit_{i-1})，adj_i = adj_{i-1} * (unit_{i-1} + acc_i - acc_{i-1}) / unit_{i-1}
     *
     * @param base 区间前最后一行（无则首行 adj=unit）
     */
    static List<FundNav> buildNavWithAdj(String code, List<EastmoneyClient.NavItem> items, FundNav base) {
        List<FundNav> entities = new ArrayList<>(items.size());
        BigDecimal prevUnit = base == null ? null : base.getUnitNav();
        BigDecimal prevAcc = base == null ? null : base.getAccNav();
        BigDecimal prevAdj = base == null ? null : base.getAdjNav();
        for (EastmoneyClient.NavItem item : items) {
            FundNav row = new FundNav();
            row.setFundCode(code);
            row.setNavDate(item.date());
            row.setUnitNav(item.unitNav());
            row.setAccNav(item.accNav());
            row.setDailyGrowth(item.dailyGrowth());
            BigDecimal adj;
            if (prevAdj == null || prevUnit == null || prevAcc == null || item.accNav() == null
                    || prevUnit.compareTo(BigDecimal.ZERO) == 0) {
                adj = item.unitNav();
            } else {
                BigDecimal ratio = prevUnit.add(item.accNav()).subtract(prevAcc)
                        .divide(prevUnit, 10, RoundingMode.HALF_UP);
                adj = prevAdj.multiply(ratio).setScale(4, RoundingMode.HALF_UP);
            }
            row.setAdjNav(adj);
            prevUnit = item.unitNav();
            prevAcc = item.accNav();
            prevAdj = adj;
            entities.add(row);
        }
        return entities;
    }

    private void upsertFundBasic(FundCheckVO check, LocalDate maxDate) {
        FundBasic fund = fundBasicMapper.selectOne(
                new LambdaQueryWrapper<FundBasic>().eq(FundBasic::getFundCode, check.code()));
        if (fund == null) {
            fund = new FundBasic();
            fund.setFundCode(check.code());
        }
        fund.setFundName(check.name());
        fund.setFundType(check.fundType());
        fund.setMarket(check.market());
        fund.setIndexCode(check.indexCode());
        fund.setIndexName(check.indexName());
        fund.setInceptionDate(check.estabDate());
        fund.setFundCompany(check.fundCompany());
        // 档案字段随 check 透传落库（V6.42）：披露净资产/费率在导入完成时即可用，不必等夜间同步刷档案。
        // null（档案未解析到）不覆盖既有值——重新导入时不能把已刷到的规模/费率抹成空。
        if (check.fundScale() != null) {
            fund.setFundScale(check.fundScale());
        }
        if (check.fundScaleDate() != null) {
            fund.setFundScaleDate(check.fundScaleDate());
        }
        if (check.mgmtFeeRate() != null) {
            fund.setMgmtFeeRate(check.mgmtFeeRate());
        }
        if (check.custFeeRate() != null) {
            fund.setCustFeeRate(check.custFeeRate());
        }
        if (check.salesFeeRate() != null) {
            fund.setSalesFeeRate(check.salesFeeRate());
        }
        fund.setStatus(1);
        if (maxDate != null) {
            fund.setLastSyncDate(maxDate);
        }
        // 导入也是一次同步动作：刷新动作时间（含时分秒，V5.49）
        fund.setLastSyncAt(LocalDateTime.now());
        if (fund.getId() == null) {
            fundBasicMapper.insert(fund);
        } else {
            fundBasicMapper.updateById(fund);
        }
    }

    /**
     * 判定场内市场：1=沪 0=深，非场内（含 ETF 联接、普通场外指数、指数 LOF）返回 null。
     *
     * <p>⚠️ V6.30 起**只用代码段规则、不再做"push2 场内行情探测"兜底**：交易所 ETF 代码段
     * （沪 51/56/58、深 159）已全覆盖，而探测只看"有没有场内报价"，会把两类基金误判成场内 ETF：
     * ① 与深市股票撞号的 00xxxx 场外基金——实测 **002611 博时黄金ETF联接C**（002611 同时是深市股票）
     * 被判为场内、走了 K 线取数路径；② 场内可交易但按**场外净值口径**取数的 LOF（16xxxx/501xxx）。
     * 原兜底注释里的历史事故（515080 误判为场外）由代码段规则本身覆盖，不受影响。
     */
    private Integer detectMarket(String code) {
        return detectMarketByCode(code);
    }

    /** 代码段规则：沪 ETF（51/56/58 开头）→ 1；深 ETF（159 开头）→ 0；其余 → null（场外口径） */
    private Integer detectMarketByCode(String code) {
        if (code == null) {
            return null;
        }
        // 带市场前缀/后缀的写法（如 SH510300、159915.SZ）先提取 6 位数字再套规则（V4.6 起支持这类输入）
        java.util.regex.Matcher digits = java.util.regex.Pattern.compile("(\\d{6})").matcher(code);
        if (!digits.find()) {
            return null;
        }
        String six = digits.group(1);
        if (six.startsWith("51") || six.startsWith("56") || six.startsWith("58")) {
            return 1;
        }
        if (six.startsWith("159")) {
            return 0;
        }
        return null;
    }

    /**
     * 导入起点：近 15 年与成立日的较大者（用户口径，V5.3 由 10 年放宽到 15 年）。
     * 15:30 日K全量覆盖走同一函数——已导入的基金无需手动重导，次日覆盖自动补长到 15 年。
     */
    static LocalDate historyBegin(LocalDate estabDate) {
        LocalDate windowStart = LocalDate.now().minusYears(15);
        return estabDate != null && estabDate.isAfter(windowStart) ? estabDate : windowStart;
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
