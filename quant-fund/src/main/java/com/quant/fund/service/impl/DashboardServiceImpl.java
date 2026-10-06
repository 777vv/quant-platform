package com.quant.fund.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.quant.common.util.JsonUtils;
import com.quant.fund.dto.AssetSummaryVO;
import com.quant.fund.dto.DashboardOverviewVO;
import com.quant.fund.dto.HoldingVO;
import com.quant.fund.dto.IndexBoardVO;
import com.quant.fund.dto.IndexQuoteVO;
import com.quant.fund.entity.IndexQuote;
import com.quant.fund.service.DashboardService;
import com.quant.fund.service.ProfitStatsService;
import com.quant.fund.service.SyncService;
import com.quant.fund.service.SyncSummaryService;
import com.quant.fund.service.TradeService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import tools.jackson.core.type.TypeReference;

/**
 * 仪表盘聚合实现（FR1）：行情与持仓来源于既有服务，本类只做组装，不重复取数逻辑。
 */
@Service
public class DashboardServiceImpl implements DashboardService {

    /** 行情降级标记键（值为 1 表示当前展示库内快照） */
    private static final String CACHE_INDEX_DEGRADED = "quote:index:degraded";

    /** 迷你线缺失标记键（值为 1 表示本轮有指数走势采样拉取失败） */
    private static final String CACHE_TREND_DEGRADED = "quote:index:trend:degraded";

    /** 指数近 30 交易日走势的 Redis 键前缀（后接 secid；由行情刷新任务写入） */
    private static final String TREND_KEY_PREFIX = "quote:index:trend30:";

    private final SyncService syncService;

    private final TradeService tradeService;

    private final ProfitStatsService profitStatsService;

    private final SyncSummaryService syncSummaryService;

    private final StringRedisTemplate redisTemplate;

    public DashboardServiceImpl(SyncService syncService, TradeService tradeService,
                                ProfitStatsService profitStatsService, SyncSummaryService syncSummaryService,
                                StringRedisTemplate redisTemplate) {
        this.syncService = syncService;
        this.tradeService = tradeService;
        this.profitStatsService = profitStatsService;
        this.syncSummaryService = syncSummaryService;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public List<IndexQuoteVO> indices() {
        List<IndexQuote> quotes = syncService.getIndexQuotes();
        List<IndexQuoteVO> items = new ArrayList<>(quotes.size());
        for (IndexQuote quote : quotes) {
            items.add(new IndexQuoteVO(quote.getIndexCode(), quote.getIndexName(), quote.getRegion(),
                    quote.getLastPrice(), quote.getChangeAmt(), quote.getChangePct(), quote.getQuoteTime(),
                    readTrend(quote.getIndexCode())));
        }
        return items;
    }

    @Override
    public IndexBoardVO indexBoard() {
        List<IndexQuoteVO> quotes = indices();
        // 降级标记由 SyncService 在拉取成功/失败时写入 Redis（10 分钟窗口）
        boolean degraded = Boolean.TRUE.equals(redisTemplate.hasKey(CACHE_INDEX_DEGRADED))
                && "1".equals(redisTemplate.opsForValue().get(CACHE_INDEX_DEGRADED));
        boolean trendMissing = "1".equals(redisTemplate.opsForValue().get(CACHE_TREND_DEGRADED));
        return new IndexBoardVO(quotes, degraded, trendMissing);
    }

    @Override
    public void forceRefreshIndices() {
        // 与定时任务同一路径（含 Redisson 锁、迷你线构建与降级标记），但**忽略迷你线降级窗口**：
        // 手动刷新是用户的显式请求，即使处于数据源封堵窗口也应真试一次（受总预算约束，不会挂死）；
        // 拉取失败时抛业务异常由接口层转成友好提示，看板继续显示库内快照
        syncService.refreshIndexQuotes(true);
    }

    @Override
    public DashboardOverviewVO overview() {
        List<HoldingVO> holdings = tradeService.holdings();
        holdings.sort(Comparator.comparing((HoldingVO holding) -> nvl(holding.marketValue())).reversed());
        // 占比分母改为总资产（含现金），饼图补"现金"份额【V1.9 ㊾】
        AssetSummaryVO assets = profitStatsService.summary();
        BigDecimal total = nvl(assets.totalAssets());
        BigDecimal cash = nvl(assets.cashBalance());
        List<DashboardOverviewVO.HoldingBrief> briefs = new ArrayList<>();
        List<DashboardOverviewVO.AllocationItem> allocation = new ArrayList<>();
        for (int i = 0; i < holdings.size(); i++) {
            HoldingVO holding = holdings.get(i);
            BigDecimal weight = weightOf(holding.marketValue(), total);
            allocation.add(new DashboardOverviewVO.AllocationItem(holding.fundCode(), holding.fundName(),
                    holding.marketValue(), weight));
            if (i < 5) {
                briefs.add(new DashboardOverviewVO.HoldingBrief(holding.fundCode(), holding.fundName(),
                        holding.marketValue(), weight, holding.dayPnl(), holding.floatingPnl()));
            }
        }
        if (cash.compareTo(BigDecimal.ZERO) > 0) {
            allocation.add(new DashboardOverviewVO.AllocationItem("CASH", "现金", cash, weightOf(cash, total)));
        }
        List<DashboardOverviewVO.SyncStatusItem> syncStatus = syncSummaryService.summary();
        return new DashboardOverviewVO(briefs, profitStatsService.movers(), allocation, syncStatus,
                syncSummaryService.summaryText(syncStatus));
    }

    /** 占总资产比（%；分母为 0 或市值为空时计 0） */
    private BigDecimal weightOf(BigDecimal marketValue, BigDecimal totalAssets) {
        if (totalAssets.compareTo(BigDecimal.ZERO) <= 0 || marketValue == null) {
            return BigDecimal.ZERO;
        }
        return marketValue.multiply(BigDecimal.valueOf(100)).divide(totalAssets, 2, RoundingMode.HALF_UP);
    }

    /** 读取某指数的近 30 交易日走势（Redis String，值为 TrendSample 列表 JSON） */
    private List<IndexQuoteVO.TrendPoint> readTrend(String secid) {
        String cached = redisTemplate.opsForValue().get(TREND_KEY_PREFIX + secid);
        List<IndexQuoteVO.TrendPoint> points = new ArrayList<>();
        if (cached == null) {
            return points;
        }
        List<TrendSample> samples = JsonUtils.mapper().readValue(cached,
                new TypeReference<List<TrendSample>>() {
                });
        for (TrendSample sample : samples) {
            points.add(new IndexQuoteVO.TrendPoint(sample.time(), sample.price()));
        }
        return points;
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
