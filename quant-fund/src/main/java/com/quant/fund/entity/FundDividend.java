package com.quant.fund.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 基金分红记录（东财分红送配页口径）。
 * 用于行情图上的分红标识【q】：标记的是**基金除息日**（基金层面的事件），
 * 与用户自录的分红流水（trade_flow.type=3）语义不同，两者在接口层按日期合并去重。
 */
@Data
@TableName("fund_dividend")
public class FundDividend {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 基金代码 */
    private String fundCode;

    /** 权益登记日 */
    private LocalDate recordDate;

    /** 除息日（图上标记取这一天，与 K 线交易日对齐） */
    private LocalDate exDate;

    /** 分红发放日 */
    private LocalDate payDate;

    /** 每 10 份派现金（元）；解析不到为 null */
    private BigDecimal per10Amount;

    /** 数据来源标记：EASTMONEY_FHSP=东财分红送配页 */
    private String source;

    /** 抓取时间 */
    private LocalDateTime createdAt;
}
