package com.quant.fund.service;

import java.util.List;

import com.quant.fund.dto.AssetSummaryVO;
import com.quant.fund.dto.DashboardOverviewVO;
import com.quant.fund.dto.ProfitCurveVO;

/**
 * 收益统计引擎（FR1，M4-01）：
 * 以交易流水为唯一事实源，结合 ETF 前复权收盘价 / 场外单位净值，
 * 逐日重建持仓市值与净投入，得到累计收益序列与资产总览。
 */
public interface ProfitStatsService {

    /**
     * 资产总览：总市值 / 成本 / 当日与浮动与已实现盈亏 / 近 7 日收益。
     */
    AssetSummaryVO summary();

    /**
     * 收益曲线：区间内每日累计收益（元）+ 沪深300 同区间涨跌幅（%）+ 月度汇总。
     *
     * @param range 区间代码：1M/3M/6M/YTD/1Y/3Y/ALL
     */
    ProfitCurveVO curve(String range);

    /**
     * 收益日历（V6.07）：某个自然年的逐日收益（额 + 率）+ 自然月汇总 + 全年汇总。
     * 不拉取沪深300 基准（日历用不到），因此不含任何外部数据源依赖。
     *
     * @param year 年份（如 2026）
     */
    com.quant.fund.dto.ProfitCalendarVO calendar(int year);

    /**
     * 自选基金近 7 日涨跌幅榜（降序；区间两端缺行情的基金跳过）。
     */
    List<DashboardOverviewVO.MoverItem> movers();
}
