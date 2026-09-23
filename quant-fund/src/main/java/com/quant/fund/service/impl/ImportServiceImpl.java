package com.quant.fund.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.quant.common.exception.BizException;
import com.quant.fund.client.EastmoneyClient;
import com.quant.fund.dto.FundCheckVO;
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
import com.quant.fund.service.ImportService;
import com.quant.fund.service.TaskProgressStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 基金数据导入服务实现（FR3）：校验 -> 异步导入 ETF日K(前复权)/场外净值(含复权净值)/跟踪指数估值。
 * 说明：delete+批量插入 用 TransactionTemplate 保证原子（@Async 自调用不走代理）。
 */
@Service
public class ImportServiceImpl implements ImportService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImportServiceImpl.class);

    private static final String SOURCE_CSINDEX = "CSINDEX";

    private static final int NAV_PAGE_SIZE = 20;

    private final EastmoneyClient client;

    private final FundBasicMapper fundBasicMapper;

    private final FundEtfKlineMapper klineMapper;

    private final FundNavMapper navMapper;

    private final IndexValuationMapper valuationMapper;

    private final SyncLogMapper syncLogMapper;

    private final TaskProgressStore progressStore;

    private final TransactionTemplate transactionTemplate;

    public ImportServiceImpl(EastmoneyClient client, FundBasicMapper fundBasicMapper, FundEtfKlineMapper klineMapper,
                             FundNavMapper navMapper, IndexValuationMapper valuationMapper, SyncLogMapper syncLogMapper,
                             TaskProgressStore progressStore, TransactionTemplate transactionTemplate) {
        this.client = client;
        this.fundBasicMapper = fundBasicMapper;
        this.klineMapper = klineMapper;
        this.navMapper = navMapper;
        this.valuationMapper = valuationMapper;
        this.syncLogMapper = syncLogMapper;
        this.progressStore = progressStore;
        this.transactionTemplate = transactionTemplate;
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
                    inPool, removedFromPool, lastSyncDate);
        }
        // 有场内行情 → ETF；否则为场外指数基金
        Integer market = detectMarket(fundCode);
        Integer fundType = market != null ? FundTypeEnum.ETF.getCode() : FundTypeEnum.OTC.getCode();
        String marketCode = market == null ? null : (market == 1 ? "SH" : "SZ");
        return new FundCheckVO(fundCode, profile.name(), fundType, marketCode, profile.fundType(), profile.company(),
                profile.indexCode(), profile.indexName(), profile.estabDate(), true, null,
                inPool, removedFromPool, lastSyncDate);
    }

    @Override
    public String importFund(String fundCode) {
        FundCheckVO check = check(fundCode);
        if (!check.supported()) {
            throw new BizException(check.reason());
        }
        String taskId = UUID.randomUUID().toString().replace("-", "");
        progressStore.save(new TaskProgressVO(taskId, TaskProgressVO.RUNNING, "校验通过，开始导入", 0, 0, null));
        runImport(taskId, check);
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

    @Async("taskExecutor")
    protected void runImport(String taskId, FundCheckVO check) {
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
            if (check.indexCode() != null) {
                progressStore.save(new TaskProgressVO(taskId, TaskProgressVO.RUNNING,
                        "同步跟踪指数估值: " + check.indexName(), 0, 0, null));
                importValuation(check.indexCode(), historyBegin(null));
            }
            upsertFundBasic(check, maxDate);
            writeLog(SyncTypeEnum.HISTORY, code, true, 0, null, startAt);
            progressStore.save(new TaskProgressVO(taskId, TaskProgressVO.DONE, "导入完成", 0, 0, null));
            LOGGER.info("基金[{}]历史导入完成，数据截止 {}", code, maxDate);
        } catch (Exception e) {
            LOGGER.error("基金[{}]历史导入失败", code, e);
            writeLog(SyncTypeEnum.HISTORY, code, false, 0, e.getMessage(), startAt);
            progressStore.save(new TaskProgressVO(taskId, TaskProgressVO.FAILED, "导入失败", 0, 0, e.getMessage()));
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
        progressStore.save(new TaskProgressVO(taskId, TaskProgressVO.RUNNING, "写入日K",
                entities.size(), entities.size(), null));
        transactionTemplate.executeWithoutResult(status -> {
            klineMapper.delete(new LambdaQueryWrapper<FundEtfKline>().eq(FundEtfKline::getFundCode, check.code()));
            Db.saveBatch(entities, 500);
        });
        return entities.isEmpty() ? null : entities.get(entities.size() - 1).getTradeDate();
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
        fund.setStatus(1);
        if (maxDate != null) {
            fund.setLastSyncDate(maxDate);
        }
        if (fund.getId() == null) {
            fundBasicMapper.insert(fund);
        } else {
            fundBasicMapper.updateById(fund);
        }
    }

    /**
     * 判定场内市场：1=沪 0=深，非场内返回 null。
     * 规则优先级：① 代码段规则（确定性，不受网络影响）——沪 ETF 51/56/58 开头、深 ETF 159 开头；
     * ② 场内行情探测兜底（覆盖未纳入规则的少数品种；探测走外部接口，链路抖动时可能失败）。
     * 背景实测：曾因探测单次失败把 515080（沪 ETF）误判为场外基金，导致 K 线缺失、15:30 同步任务不覆盖。
     */
    private Integer detectMarket(String code) {
        Integer byCode = detectMarketByCode(code);
        if (byCode != null) {
            return byCode;
        }
        Optional<EastmoneyClient.EtfRealtime> sh = client.fetchEtfRealtime(1, code);
        if (sh.isPresent()) {
            return 1;
        }
        Optional<EastmoneyClient.EtfRealtime> sz = client.fetchEtfRealtime(0, code);
        if (sz.isPresent()) {
            return 0;
        }
        return null;
    }

    /** 代码段规则：沪 ETF（51/56/58 开头）→ 1；深 ETF（159 开头）→ 0；其余 → null */
    /**
     * 判定场内市场：1=沪 0=深，非场内返回 null。
     * 规则优先级：① 代码段规则（确定性，不受网络影响）——沪 ETF 51/56/58 开头、深 ETF 159 开头；
     * ② 场内行情探测兜底（覆盖未纳入规则的少数品种；探测走外部接口，链路抖动时可能失败）。
     * 背景实测：曾因探测单次失败把 515080（沪 ETF）误判为场外基金，导致 K 线缺失、15:30 同步任务不覆盖。
     *
     * <p>V4.6：非 6 位代码（如带市场前缀的写法）直接跳过规则①走探测——它们本来就不是
     * 标准 ETF 代码，命中不了 51/56/58/159 前缀；真正的格式判断在 check() 里由东财搜索接口完成。
     */
    private Integer detectMarketByCode(String code) {
        if (code == null || code.length() != 6) {
            return null;
        }
        if (code.startsWith("51") || code.startsWith("56") || code.startsWith("58")) {
            return 1;
        }
        if (code.startsWith("159")) {
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
