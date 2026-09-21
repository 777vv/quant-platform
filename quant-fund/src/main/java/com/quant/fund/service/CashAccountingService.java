package com.quant.fund.service;

import java.math.BigDecimal;

/**
 * 账户资金口径服务（现金余额的唯一计算来源）。
 *
 * <p>口径定义（V1.9 ㊾ 起沿用，仪表盘与转出额度校验共用同一份实现，避免两处算法漂移）：
 * <pre>
 *   净投入 = Σ买入(含费) − Σ卖出净额 − Σ分红净额      （投入持仓的部分）
 *   现金余额 = (Σ资金转入 − Σ资金转出) − 净投入
 *   总资产 = 持仓市值 + 现金余额
 * </pre>
 *
 * <p>⚠️ 已知口径限制（待决策，见技术文档 V2.3 备注）：净投入只统计**当前仍在自选池**（status=1）
 * 且有流水的基金，因此把一只已清仓的基金移出自选后，它的历史买卖对净投入的影响不再计入，
 * 现金余额与已实现盈亏会出现缺口。改动会影响既有账户显示，需先确认口径再动。
 */
public interface CashAccountingService {

    /** 账户级净转入（Σ转入 − Σ转出，仅统计 fund_code 为空的划转流水） */
    BigDecimal transferNetIn();

    /** 净投入（投入持仓的净资金，定义见类注释） */
    BigDecimal netInvested();

    /** 现金余额 = 净转入 − 净投入 */
    BigDecimal cashBalance();

    /**
     * 可用于转出的上限（元）= max(现金余额, 0)。
     * 现金为负（数据异常或口径缺口）时不接受任何转出，避免越转越负。
     */
    default BigDecimal transferableLimit() {
        return cashBalance().max(BigDecimal.ZERO);
    }
}
