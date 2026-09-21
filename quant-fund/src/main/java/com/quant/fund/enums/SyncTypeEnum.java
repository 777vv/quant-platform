package com.quant.fund.enums;

/**
 * 同步类型（sync_log.sync_type）
 */
public enum SyncTypeEnum {

    /** 历史导入 */
    HISTORY,

    /** ETF日K同步（全量覆盖） */
    ETF_DAILY,

    /** 场外净值同步 */
    NAV,

    /** 指数估值同步 */
    VALUATION,

    /** 全球指数行情刷新 */
    INDEX_QUOTE,

    /** 策略信号计算（每日 21:00） */
    SIGNAL,

    /** 手动单基金增量 */
    MANUAL,

    /** 自动同步（V2.2：非持仓每日 17:00 / 持仓盘中每 10 分钟） */
    AUTO
}
