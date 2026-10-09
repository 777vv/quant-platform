package com.quant.fund.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.Data;

/**
 * 收益日历（V6.07）：某个自然年的逐日收益 + 自然月汇总 + 全年汇总。
 *
 * <p>口径（与平台既有曲线/KPI 一致）：
 * ① 每日收益额 = 相邻两个数据日的<b>累计收益之差</b>（累计收益 = 当日持仓市值 − 当日净投入）——
 *    与近 7 日柱图、月度汇总同一口径，一个自然月内各日相加恒等于「本月收益」；
 * ② 每日收益率 = 日收益额 ÷ <b>前一数据日的持仓市值</b>（与 KPI 月/年收益率同分母口径），
 *    基准市值缺失或为 0 时为 null（前端显示 --）；
 * ③ 只含"有行情数据的日子"（各基金价格日并集，非交易日/无数据日不下发）。
 *
 * <p>已知边界：与平台其它收益口径一致，**移出自选池的历史基金不计入**。
 */
@Data
public class ProfitCalendarVO {

    /** 年份 */
    private int year;

    /** 是否有可用数据（无持仓流水时为 false，前端展示空态） */
    private boolean hasData;

    /** 逐日收益（只含有数据的日子） */
    private List<DayPoint> days;

    /** 自然月汇总（只含有数据的月份；月份升序） */
    private List<MonthPoint> months;

    /** 全年收益（元） */
    private BigDecimal yearPnl;

    /** 全年收益率（%）：分母 = 上年末最后数据日持仓市值；无基准时为 null */
    private BigDecimal yearPct;

    /** 全年交易日（有数据的日子）数 */
    private int yearDayCount;

    /**
     * 单日收益
     */
    public record DayPoint(
            /** 日期（yyyy-MM-dd） */
            String date,
            /** 当日收益（元）= 当日累计收益 − 前一数据日累计收益 */
            BigDecimal pnl,
            /** 当日收益率（%）= 当日收益 ÷ 前一数据日持仓市值；无基准时为 null */
            BigDecimal pct,
            /** 当日持仓市值（元）——作为次日的收益率基准 */
            BigDecimal marketValue) {
    }

    /**
     * 自然月汇总
     */
    public record MonthPoint(
            /** 月份（yyyy-MM） */
            String month,
            /** 当月收益（元） */
            BigDecimal pnl,
            /** 当月收益率（%）：分母 = 上月末（或上月最后一个数据日）持仓市值；无基准时为 null */
            BigDecimal pct,
            /** 当月有数据的天数 */
            int dayCount) {
    }
}
