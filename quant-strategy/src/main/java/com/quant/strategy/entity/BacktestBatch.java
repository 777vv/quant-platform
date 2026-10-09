package com.quant.strategy.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 批量回测批次（V5.96）：一批 = 一套策略参数 × N 只基金，每只基金的结果仍是 backtest_record 一行（带 batch_id），
 * 本表只存批次级口径（策略/区间/初始资金模式）与实时进度计数（成功/失败数随每只完成即累加）。
 */
@Data
@TableName("backtest_batch")
public class BacktestBatch {

    /** 运行中 */
    public static final int STATUS_RUNNING = 0;
    /** 已完成（成功+失败=总数，不代表全部成功） */
    public static final int STATUS_FINISHED = 1;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 策略类型码（整批统一） */
    private String strategyType;

    /** 策略参数快照 JSON（整批统一） */
    private String params;

    /** 回测开始日期（整批统一） */
    private LocalDate startDate;

    /** 回测结束日期（整批统一） */
    private LocalDate endDate;

    /** 统一初始资金（手填模式用；自动模式为 NULL，各基金按"满仓份额×开始日价×1.01"自算） */
    private BigDecimal initialCapital;

    /** 基金总数 */
    private Integer totalCount;

    /** 成功数（每完成一只即累加，实时进度） */
    private Integer successCount;

    /** 失败数（每完成一只即累加，实时进度） */
    private Integer failCount;

    /** 0运行中 1已完成 */
    private Integer status;

    /** 操作账号（用户名；取不到回退用户 ID） */
    private String createdBy;

    /** 发起时间（操作时间） */
    private LocalDateTime createdAt;

    /** 完成时间（全部基金跑完） */
    private LocalDateTime finishedAt;
}
