package com.quant.fund.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 全球指数快照
 */
@Data
@TableName("index_quote")
public class IndexQuote {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 东财 secid（如 100.DJIA、124.HSTECH），唯一 */
    private String indexCode;

    /** 指数名称（配置文件维护） */
    private String indexName;

    /** 区域分组 CN/HK/US/ASIA/EU（前端看板分组） */
    private String region;

    /** 最新点位 */
    private BigDecimal lastPrice;

    /** 涨跌额 */
    private BigDecimal changeAmt;

    /** 涨跌幅% */
    private BigDecimal changePct;

    /** 行情时间（东财 f124 时间戳转上海时区） */
    private LocalDateTime quoteTime;

    /** 创建时间（库默认值） */
    private LocalDateTime createdAt;

    /** 更新时间（库自动维护） */
    private LocalDateTime updatedAt;
}
