package com.quant.fund.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 市场休市日（A股）
 *
 * <p>用途（V5.24）：把"今天是不是交易日"从"周一到周五"修正为"工作日且不在休市名单"，
 * 供定时任务（信号推送、盘中同步、档案刷新）在节假日不动作。
 *
 * <p>名单来源两类：
 * ① manual：交易所公告的法定节假日休市安排（每年公布次年安排后补录，见 schema.sql 说明）；
 * ② observed：平台盘面自判确认的休市日（连续两轮成功请求但无当天 bar，见 SyncServiceImpl）。
 *
 * <p>周末不落库：判定时按星期几直接排除，避免名单里堆满无意义的周六日。
 */
@Data
@TableName("market_holiday")
public class MarketHoliday {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 休市日期 */
    private LocalDate holidayDate;

    /** 休市说明（如 中秋节、国庆节；盘面自判时写入判定依据） */
    private String holidayName;

    /** 来源：manual=人工/内置名单，observed=平台盘面自判 */
    private String source;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
