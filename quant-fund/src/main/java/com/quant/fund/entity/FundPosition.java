package com.quant.fund.entity;

import java.math.BigDecimal;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 持仓汇总（由流水重算，position 为 MySQL 关键字故表名 fund_position）
 */
@Data
@TableName("fund_position")
public class FundPosition {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 基金代码（唯一），流水重算的冗余汇总表 */
    private String fundCode;

    /** 持有份额 */
    private BigDecimal totalShare;

    /** 剩余持仓摊薄总成本（含费用） */
    private BigDecimal totalCost;

    /** 摊薄成本价 = totalCost/totalShare */
    private BigDecimal avgCostPrice;

    /** 累计已实现盈亏（卖出+分红-结转成本） */
    private BigDecimal realizedPnl;
}
