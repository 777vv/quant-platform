package com.quant.fund.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 同步日志
 */
@Data
@TableName("sync_log")
public class SyncLog {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 基金代码（NULL=全局任务，如 ETF_DAILY 全量覆盖） */
    private String fundCode;

    /** 同步类型（SyncTypeEnum：HISTORY/ETF_DAILY/NAV/VALUATION/MANUAL） */
    private String syncType;

    /** 1=成功 0=失败 */
    private Integer status;

    /** 本次新增/处理条数 */
    private Integer recordCount;

    /** 失败原因（截断500字符） */
    private String errorMsg;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;
}
