package com.quant.fund.service;

import java.util.List;

import com.quant.fund.dto.TaskProgressVO;
import com.quant.fund.entity.IndexQuote;

/**
 * 数据同步服务（FR5 定时 + FR2 手动）
 */
public interface SyncService {

    /** 手动单基金增量同步（异步），返回进度 taskId */
    String manualSync(String fundCode);

    /** 手动同步进度查询 */
    TaskProgressVO manualProgress(String taskId);

    /** ETF 日K全量覆盖同步（15:30，修正前复权口径） */
    void syncAllEtfDaily();

    /** 场外净值同步（20:00 与次日 7:00 补拉共用，幂等） */
    void syncAllOtcNav();

    /** 指数估值增量同步（20:30） */
    void syncValuation();

    /**
     * 全球指数行情刷新（定时任务路径）+ 写库与缓存标记。
     * 迷你线缺失时会在 10 分钟降级窗口内跳过重建，避免每次打开看板白等一次构建预算。
     */
    void refreshIndexQuotes();

    /**
     * 全球指数行情刷新。
     *
     * @param force 用户手动触发（看板"刷新"按钮）时传 true：**忽略迷你线降级窗口**
     *              强制重新尝试构建（仍受总时间预算约束，不会挂死）；定时任务传 false。
     */
    void refreshIndexQuotes(boolean force);

    /**
     * 读取全球指数：**只读库内快照，不触发外部拉取**（仪表盘秒开）；
     * 数据新鲜度由定时任务维护，用户点"刷新"走 {@link #refreshIndexQuotes(boolean)}。
     */
    List<IndexQuote> getIndexQuotes();

    /**
     * 自动同步（V2.2）：非持仓的自选基金每日 17:00（交易日）增量同步。
     */
    void syncNonHoldingFunds();

    /**
     * 自动同步（V2.2）：持仓基金盘中每 10 分钟增量同步（仅交易时段 9:30-11:30 / 13:00-15:00，任务内自判）。
     */
    void syncHoldingFundsIntraday();
}
