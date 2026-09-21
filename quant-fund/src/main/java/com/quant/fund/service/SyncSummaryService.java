package com.quant.fund.service;

import java.util.List;

import com.quant.fund.dto.DashboardOverviewVO;

/**
 * 数据同步状态汇总（FR1，M4-09）：按基金检查本地数据新鲜度，
 * 结果缓存 Redis 供仪表盘速览读取，22:00 定时任务刷新。
 */
public interface SyncSummaryService {

    /** 状态标识：正常（前端与告警任务按此判定） */
    String STATUS_NORMAL = "NORMAL";

    /** 状态标识：滞后（超出宽限未追平） */
    String STATUS_LAGGING = "LAGGING";

    /**
     * 全量计算各基金同步状态并刷新缓存（定时任务入口）。
     */
    List<DashboardOverviewVO.SyncStatusItem> computeSummary();

    /**
     * 读取缓存的汇总结果；缓存缺失时降级现算。
     */
    List<DashboardOverviewVO.SyncStatusItem> summary();

    /**
     * 汇总文案，如 "3/4 正常"；全部正常时 "4/4 正常"。
     */
    default String summaryText(List<DashboardOverviewVO.SyncStatusItem> items) {
        long normal = items.stream().filter(item -> STATUS_NORMAL.equals(item.status())).count();
        return normal + "/" + items.size() + " 正常";
    }
}
