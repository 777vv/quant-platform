package com.quant.fund.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 场外基金净值
 */
@Data
@TableName("fund_nav")
public class FundNav {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 基金代码 */
    private String fundCode;

    /** 净值日期 */
    private LocalDate navDate;

    /** 单位净值（每日一个） */
    private BigDecimal unitNav;

    /** 累计净值（单位净值+历史分红之和，不含复利） */
    private BigDecimal accNav;

    /** 复权净值（分红再投资复利调整，本地计算，技术文档 6.1） */
    private BigDecimal adjNav;

    /** 日增长率%（东财原始值） */
    private BigDecimal dailyGrowth;
}
