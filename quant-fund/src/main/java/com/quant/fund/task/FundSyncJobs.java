package com.quant.fund.task;

import com.quant.common.log.TraceIdGenerator;
import com.quant.fund.service.SyncService;
import com.quant.fund.service.SyncSummaryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 基金数据定时任务（FR5，技术文档 6.6）：
 * 任务级 traceId 便于从日志溯源单次执行；互斥由 SyncService 内的 Redisson 锁保证。
 */
@Component
public class FundSyncJobs {

    private static final Logger LOGGER = LoggerFactory.getLogger(FundSyncJobs.class);

    private final SyncService syncService;

    private final SyncSummaryService syncSummaryService;

    public FundSyncJobs(SyncService syncService, SyncSummaryService syncSummaryService) {
        this.syncService = syncService;
        this.syncSummaryService = syncSummaryService;
    }

    /** 每交易日 15:30 ETF 日K全量覆盖（修正前复权口径）——当天的收盘价由这轮定稿 */
    @Scheduled(cron = "0 30 15 * * MON-FRI")
    public void syncEtfDaily() {
        runWithTrace("etf:daily", syncService::syncAllEtfDaily);
    }

    /**
     * 每交易日 15:05 档案（规模/费率）强制刷新（用户口径：收盘后立即再刷一次最新规模），
     * 并把当日规模快照落进 fund_scale_history（行情图「基金规模」副图数据源）。
     */
    @Scheduled(cron = "0 5 15 * * MON-FRI")
    public void refreshProfiles() {
        runWithTrace("profile:refresh", syncService::refreshAllProfiles);
    }

    /** 每交易日 20:00 场外净值同步 */
    @Scheduled(cron = "0 0 20 * * MON-FRI")
    public void syncNav() {
        runWithTrace("nav", syncService::syncAllOtcNav);
    }

    /** 次日 07:00 净值补拉（幂等，未公布的此处补齐） */
    @Scheduled(cron = "0 0 7 * * *")
    public void compensateNav() {
        runWithTrace("nav:compensate", syncService::syncAllOtcNav);
    }

    /** 每交易日 20:30 指数估值增量同步 */
    @Scheduled(cron = "0 30 20 * * MON-FRI")
    public void syncValuation() {
        runWithTrace("valuation", syncService::syncValuation);
    }

    /** 交易时段每 5 分钟刷新全球指数缓存（9:00-23:59） */
    @Scheduled(cron = "0 */5 9-23 * * *")
    public void refreshIndexQuotes() {
        try {
            syncService.refreshIndexQuotes();
        } catch (Exception e) {
            LOGGER.error("全球指数行情刷新失败: {}", e.getMessage());
        }
    }

    /**
     * 每 10 分钟：全部自选 ETF 盘中增量同步（V5.3 起自选+持仓全覆盖）。
     * cron 只能限定到 MON-FRI，交易时段（9:30-11:30 / 13:00-15:00）由服务内自判，
     * 非时段直接空转返回；节假日由数据源自判（成功请求但无当天 bar），当天剩余时间跳过。
     */
    @Scheduled(cron = "0 */10 * * * MON-FRI")
    public void syncWatchIntraday() {
        runWithTrace("sync:watch", syncService::syncWatchFundsIntraday);
    }

    /** 每日 22:00 数据同步状态汇总（刷新仪表盘速览缓存） */
    @Scheduled(cron = "0 0 22 * * *")
    public void syncSummary() {
        runWithTrace("sync:summary", syncSummaryService::computeSummary);
    }

    private void runWithTrace(String jobName, Runnable task) {
        MDC.put("traceId", TraceIdGenerator.nextJob(jobName));
        try {
            task.run();
        } finally {
            MDC.clear();
        }
    }
}
