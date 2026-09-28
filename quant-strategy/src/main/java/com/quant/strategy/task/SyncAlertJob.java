package com.quant.strategy.task;

import java.util.List;

import com.quant.common.log.JobLogs;
import com.quant.fund.dto.DashboardOverviewVO;
import com.quant.fund.service.SyncSummaryService;
import com.quant.strategy.notify.NotifyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 数据同步异常告警任务（FR5，M4-09）：
 * 22:00 汇总任务刷新缓存后，本任务于 22:05 读取汇总结果，对滞后基金发送告警邮件。
 * 分两个任务而非合并，是为了保持模块依赖方向（fund 不感知通知，策略模块负责通知编排）。
 */
@Component
public class SyncAlertJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(SyncAlertJob.class);

    private final SyncSummaryService syncSummaryService;

    private final NotifyService notifyService;

    public SyncAlertJob(SyncSummaryService syncSummaryService, NotifyService notifyService) {
        this.syncSummaryService = syncSummaryService;
        this.notifyService = notifyService;
    }

    /**
     * 每日 22:05 检查同步状态并对滞后基金告警。
     * 统一走 {@link JobLogs#run}：打印「开始执行 / 执行结束（耗时）」+ 任务级 traceId 串联本轮日志。
     */
    @Scheduled(cron = "0 5 22 * * *")
    public void alertLagging() {
        JobLogs.run("sync:alert", () -> {
            List<DashboardOverviewVO.SyncStatusItem> lagging = syncSummaryService.summary().stream()
                    .filter(item -> SyncSummaryService.STATUS_LAGGING.equals(item.status()))
                    .toList();
            if (lagging.isEmpty()) {
                LOGGER.info("同步状态检查通过，无滞后基金");
                return;
            }
            LOGGER.warn("检测到 {} 只基金数据滞后，触发告警邮件", lagging.size());
            notifyService.sendSyncAlert(lagging);
        });
    }
}
