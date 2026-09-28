package com.quant.fund.task;

import java.time.DayOfWeek;
import java.time.LocalDate;

import com.quant.common.log.JobLogs;
import com.quant.fund.service.SyncService;
import com.quant.fund.service.SyncSummaryService;
import com.quant.fund.service.TradingCalendarService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 基金数据定时任务（FR5，技术文档 6.6）：
 * 每个任务统一走 {@link JobLogs#run}，打印「开始执行 / 执行结束（耗时）」并用任务级 traceId
 * 串联本轮全部日志（V5.38，用户要求：便于按 traceId 从日志文件排查问题）；
 * 互斥由 SyncService 内的 Redisson 锁保证。
 */
@Component
public class FundSyncJobs {

    private static final Logger LOGGER = LoggerFactory.getLogger(FundSyncJobs.class);

    private final SyncService syncService;

    private final SyncSummaryService syncSummaryService;

    /** 交易日判定（V5.33）：法定节假日里「收盘后同步」不再空跑/撞限流 */
    private final TradingCalendarService tradingCalendarService;

    public FundSyncJobs(SyncService syncService, SyncSummaryService syncSummaryService,
                        TradingCalendarService tradingCalendarService) {
        this.syncService = syncService;
        this.syncSummaryService = syncSummaryService;
        this.tradingCalendarService = tradingCalendarService;
    }

    /**
     * 每交易日 15:30 ETF 日K全量覆盖（修正前复权口径）——当天的收盘价由这轮定稿。
     * 交易日闸门（V5.33）：节假日数据源没有新 bar，跑了只会空转甚至撞限流（2026-09-25 中秋实测失败）。
     */
    @Scheduled(cron = "0 30 15 * * MON-FRI")
    public void syncEtfDaily() {
        JobLogs.run("etf:daily", () -> {
            if (!isClosedToday("etf:daily")) {
                syncService.syncAllEtfDaily();
            }
        });
    }

    /**
     * 每交易日 15:05 档案（规模/费率）强制刷新（用户口径：收盘后立即再刷一次最新规模），
     * 并把当日规模快照落进 fund_scale_history（行情图「基金规模」副图数据源）。
     */
    @Scheduled(cron = "0 5 15 * * MON-FRI")
    public void refreshProfiles() {
        JobLogs.run("profile:refresh", syncService::refreshAllProfiles);
    }

    /**
     * 每交易日 20:00 场外净值同步。
     * 交易日闸门（V5.33）：节假日净值源无新数据，空转无意义。注意闸门加在**本任务**而不是
     * 共用的 syncAllOtcNav 上——07:00 的补拉是每天（含周末节假日）的兜底，必须照常跑。
     */
    @Scheduled(cron = "0 0 20 * * MON-FRI")
    public void syncNav() {
        JobLogs.run("nav", () -> {
            if (!isClosedToday("nav")) {
                syncService.syncAllOtcNav();
            }
        });
    }

    /** 次日 07:00 净值补拉（幂等，未公布的此处补齐）——**每天跑，不加交易日闸门**（兜底性质） */
    @Scheduled(cron = "0 0 7 * * *")
    public void compensateNav() {
        JobLogs.run("nav:compensate", syncService::syncAllOtcNav);
    }

    /** 每交易日 20:30 指数估值增量同步。交易日闸门（V5.33）：节假日估值源无新数据，空转无意义。 */
    @Scheduled(cron = "0 30 20 * * MON-FRI")
    public void syncValuation() {
        JobLogs.run("valuation", () -> {
            if (!isClosedToday("valuation")) {
                syncService.syncValuation();
            }
        });
    }

    /**
     * 每 5 分钟刷新全球指数缓存（9:00-23:59）。
     * 周末跳过（V5.33）：全球休市，刷新只拿陈旧缓存；境内法定节假日**不跳**——美股等海外市场照常交易，
     * 本任务的时间跨度（到 23:59）就是为覆盖海外时段而设。
     */
    @Scheduled(cron = "0 */5 9-23 * * *")
    public void refreshIndexQuotes() {
        JobLogs.run("index:quotes", () -> {
            DayOfWeek week = LocalDate.now().getDayOfWeek();
            if (week == DayOfWeek.SATURDAY || week == DayOfWeek.SUNDAY) {
                LOGGER.info("周末全球休市，任务[index:quotes]跳过：不刷新缓存");
                return;
            }
            syncService.refreshIndexQuotes();
        });
    }

    /**
     * 每 10 分钟：全部自选 ETF 盘中增量同步（V5.3 起自选+持仓全覆盖）。
     * cron 只能限定到 MON-FRI，交易时段（9:30-11:30 / 13:00-15:00）由服务内自判，
     * 非时段直接空转返回；节假日由数据源自判（成功请求但无当天 bar），当天剩余时间跳过。
     */
    @Scheduled(cron = "0 */5 * * * MON-FRI")
    public void syncWatchIntraday() {
        JobLogs.run("sync:watch", syncService::syncWatchFundsIntraday);
    }

    /** 每日 22:00 数据同步状态汇总（刷新仪表盘速览缓存） */
    @Scheduled(cron = "0 0 22 * * *")
    public void syncSummary() {
        JobLogs.run("sync:summary", syncSummaryService::computeSummary);
    }

    /**
     * 今日是否休市（法定节假日/调休休市，周末已被 cron 排除）。
     * 命中时留一条 INFO 便于从日志确认"任务是有意跳过而非漏跑"——该日志共用本轮的 traceId。
     */
    private boolean isClosedToday(String jobName) {
        if (tradingCalendarService.isTradingDay(LocalDate.now())) {
            return false;
        }
        LOGGER.info("今日（{}）非交易日（休市名单），任务[{}]跳过：不执行", LocalDate.now(), jobName);
        return true;
    }
}
