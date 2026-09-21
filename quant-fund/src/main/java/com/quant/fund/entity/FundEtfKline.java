package com.quant.fund.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * ETF 日K（前复权）
 */
@Data
@TableName("fund_etf_kline")
public class FundEtfKline {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 基金代码 */
    private String fundCode;

    /** 交易日 */
    private LocalDate tradeDate;

    /** 开盘价（前复权） */
    private BigDecimal open;

    /** 收盘价（前复权） */
    private BigDecimal close;

    /**
     * 未复权收盘价：分红股息率等需要"真实价格"的口径用它作分母——
     * 前复权价把历史价格压低（把分红从价格里抹掉），直接当分母会系统性高估历史股息率。
     * 由每日全量覆盖同步（15:30）额外拉一次 fqt=0 写入；数据源不可用时留空，前端按缺失处理。
     */
    private BigDecimal unadjClose;

    /** 最高价（前复权） */
    private BigDecimal high;

    /** 最低价（前复权） */
    private BigDecimal low;

    /** 成交量（手） */
    private Long volume;

    /** 成交额（元） */
    private BigDecimal amount;
}
