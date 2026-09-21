package com.quant.fund.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 收益曲线（FR1，M4-02）：累计收益（元）与沪深300 基准（%）双序列 + 月度汇总
 */
public record ProfitCurveVO(
        /** 区间起始日 */
        LocalDate startDate,
        /** 区间结束日 */
        LocalDate endDate,
        /** 日期轴（yyyy-MM-dd，持仓涉及的全部价格日并集） */
        List<String> dates,
        /** 每日累计收益（元）= 当日持仓市值 - 当日净投入 */
        List<BigDecimal> pnl,
        /** 沪深300 同区间涨跌幅（%，起点为 0；基准拉取失败时元素为 null） */
        List<BigDecimal> benchmarkPct,
        /** 月度收益汇总（区间内按自然月） */
        List<MonthlyPnl> monthly,
        /** 是否有可用数据（无持仓流水时 false，前端展示空态） */
        boolean hasData) {

    /**
     * 月度收益条目
     */
    public record MonthlyPnl(
            /** 月份（yyyy-MM） */
            String month,
            /** 当月收益（元）= 月末累计收益 - 上月末（或区间起点）累计收益 */
            BigDecimal pnl) {
    }
}
