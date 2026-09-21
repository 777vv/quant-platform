package com.quant.strategy.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 回测交易明细
 */
@Data
@TableName("backtest_trade_detail")
public class BacktestTradeDetail {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属回测记录 ID */
    private Long backtestId;

    /** 成交日期（信号次日） */
    private LocalDate tradeDate;

    /** BUY/SELL */
    private String direction;

    /** 成交价：ETF=次日开盘（前复权），场外=次日净值 */
    private BigDecimal price;

    /** 成交份额 */
    private BigDecimal share;

    /** 成交金额（不含费用） */
    private BigDecimal amount;

    /** 手续费（佣金/申赎费） */
    private BigDecimal fee;

    /** 成交后剩余现金 */
    private BigDecimal cashAfter;

    /** 成交后持仓份额 */
    private BigDecimal positionAfter;

    /** 信号理由（策略给出，如"网格下穿买入"） */
    private String reason;

    /** 创建时间（库默认值） */
    private LocalDateTime createdAt;
}
