package com.quant.fund.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 交易流水（持仓唯一事实来源）
 */
@Data
@TableName("trade_flow")
public class TradeFlow {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 基金代码；资金转入/转出(类型4/5)为 NULL */
    private String fundCode;

    /** 1=买入/申购 2=卖出/赎回 3=分红（现金分红） */
    private Integer tradeType;

    /** 交易日期 */
    private LocalDate tradeDate;

    /** ETF=成交价 场外=当日单位净值 */
    private BigDecimal price;

    /** 份额（2位小数） */
    private BigDecimal share;

    /** 成交金额（不含费用） */
    private BigDecimal amount;

    /** 手续费（佣金/申赎费） */
    private BigDecimal fee;

    /** 备注 */
    private String note;

    /** 创建时间（库默认值） */
    private LocalDateTime createdAt;

    /** 更新时间（库自动维护） */
    private LocalDateTime updatedAt;
}
