package com.quant.fund.service;

import java.time.LocalDate;
import java.util.List;

import com.quant.fund.entity.MarketHoliday;

/**
 * 交易日判定（V5.24，FR5）：回答"某天是不是 A 股交易日"。
 *
 * <p>判定口径：① 周六周日一律非交易日（A 股调休上班的周六日同样不开市）；
 * ② 命中休市名单（market_holiday）非交易日；③ 其余工作日视为交易日。
 *
 * <p>为什么需要它：定时任务原先只用 cron 的 MON-FRI 近似"交易日"，遇到工作日里的法定节假日
 * （如 2026-09-25 中秋节，周五）会照常执行——信号任务会在休市日推送"上一交易日"的信号，
 * 而节后第一个真正开市的早上反而因为没有新数据而不再推送。
 */
public interface TradingCalendarService {

    /**
     * 指定日期是否 A 股交易日。
     *
     * @param date 待判定日期（null 视为非交易日）
     * @return true=交易日（工作日且不在休市名单）
     */
    boolean isTradingDay(LocalDate date);

    /** 今天是否交易日（定时任务入口用） */
    default boolean isTradingDay() {
        return isTradingDay(LocalDate.now());
    }

    /**
     * 记录平台盘面自判确认的休市日（幂等：该日期已有记录则不动）。
     *
     * @param date   确认休市的日期
     * @param reason 判定依据说明（写入 holiday_name，便于日后核对）
     */
    void recordObservedHoliday(LocalDate date, String reason);

    /**
     * 休市名单区间查询（按日期升序，供排查与展示）。
     *
     * @param from 起始日（含）
     * @param to   结束日（含）
     * @return 区间内的休市日记录
     */
    List<MarketHoliday> listHolidays(LocalDate from, LocalDate to);
}
