package com.quant.strategy.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 策略信号记录（每日 21:00 任务生成，(基金, 策略, 信号日) 唯一幂等）
 */
@Data
@TableName("signal_record")
public class SignalRecord {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 基金代码 */
    private String fundCode;

    /** 策略类型 GRID/VAL_PERCENTILE */
    private String strategyType;

    /** 信号日期 = 行情序列最后一日（净值未公布时为最近可得日） */
    private LocalDate signalDate;

    /** 方向 BUY/SELL/HOLD */
    private String direction;

    /** 信号时价格（ETF 前复权收盘价 / 场外单位净值） */
    private BigDecimal priceAt;

    /** 建议描述（含触发条件与建议操作） */
    private String suggestDesc;

    /** 0=未读 1=已读（前端点击查看后标记） */
    private Integer readFlag;

    /** 0=未通知 1=已通知（邮件摘要发送成功后标记） */
    private Integer notifiedFlag;

    /** 创建时间（库默认值） */
    private LocalDateTime createdAt;

    /** 更新时间（库自动维护） */
    private LocalDateTime updatedAt;
}
