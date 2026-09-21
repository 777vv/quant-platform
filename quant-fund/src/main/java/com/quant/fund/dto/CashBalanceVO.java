package com.quant.fund.dto;

import java.math.BigDecimal;

/**
 * 账户现金口径（转出额度校验与前端提示共用）。
 *
 * @param cashBalance       现金余额（元）= 净转入 − 净投入
 * @param transferNetIn     账户级净转入（元）= Σ转入 − Σ转出
 * @param netInvested       净投入（元）= Σ买入(含费) − Σ卖出净额 − Σ分红净额
 * @param transferableLimit 可转出上限（元）= max(现金余额, 0)
 */
public record CashBalanceVO(
        /** 现金余额（元） */
        BigDecimal cashBalance,
        /** 账户级净转入（元） */
        BigDecimal transferNetIn,
        /** 净投入（元） */
        BigDecimal netInvested,
        /** 可转出上限（元） */
        BigDecimal transferableLimit) {
}
