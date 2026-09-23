package com.quant.fund.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 基金规模历史（每日档案刷新成功后落一行，同日覆盖）。
 * ⚠️ 数据源只披露当前规模，历史无法回补——曲线自本表上线日起逐日积累。
 */
@Data
@TableName("fund_scale_history")
public class FundScaleHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 基金代码 */
    private String fundCode;

    /** 统计日期（档案刷新成功那天） */
    private LocalDate statDate;

    /** 净资产规模（亿元） */
    private BigDecimal fundScale;

    /** 规模数据截止日（东财披露，通常为季末） */
    private LocalDate scaleDate;

    /** 创建时间（库默认值） */
    private LocalDateTime createdAt;
}
