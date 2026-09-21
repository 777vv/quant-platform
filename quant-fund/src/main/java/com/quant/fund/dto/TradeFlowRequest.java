package com.quant.fund.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 交易流水新增/修改请求。
 * 校验按类型在服务层完成：基金交易须有 fund_code/价格/份额；资金转入/转出(4/5)不关联基金、仅金额生效。
 */
@Data
public class TradeFlowRequest {

    /** 基金代码（基金交易必填；资金转入/转出留空） */
    private String fundCode;

    /** 1=买入/申购 2=卖出/赎回 3=分红 4=资金转入 5=资金转出 */
    @NotNull(message = "交易类型不能为空")
    private Integer tradeType;

    /** 交易日期 */
    @NotNull(message = "交易日期不能为空")
    private LocalDate tradeDate;

    /** 价格：ETF=成交价，场外=当日单位净值（资金转入/转出忽略） */
    private BigDecimal price;

    /** 份额（2位小数；资金转入/转出忽略） */
    private BigDecimal share;

    /** 金额（不含费用）：基金交易=成交金额，转入/转出=划转金额 */
    @NotNull(message = "金额不能为空")
    private BigDecimal amount;

    /** 手续费（默认 0） */
    private BigDecimal fee = BigDecimal.ZERO;

    /** 备注（默认空） */
    private String note = "";
}
