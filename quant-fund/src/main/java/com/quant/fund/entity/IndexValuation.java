package com.quant.fund.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 指数估值历史（唯一事实源，基金经 fund_basic.index_code 关联）
 */
@Data
@TableName("index_valuation")
public class IndexValuation {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 指数代码（如 000300），估值唯一事实源（V1.1 决策：基金经关联查询） */
    private String indexCode;

    /** 交易日 */
    private LocalDate tradeDate;

    /** 市盈率（csindex 的 peg 字段） */
    private BigDecimal pe;

    /** 市净率（暂无数据源，预留） */
    private BigDecimal pb;

    /** 数据来源（CSINDEX/CSV 手动导入） */
    private String source;
}
