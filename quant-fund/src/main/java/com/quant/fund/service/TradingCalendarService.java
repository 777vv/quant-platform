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
     * 上一个交易日（不含传入日期当天，V6.60）：
     * 从 date-1 起向前找，最多回看 30 天；周末与休市名单都跳过。
     * 用于"昨日收盘价是否已定稿"这类日内判断（周一应回看到上周五，而不是自然日周日）。
     *
     * @param date 基准日期
     * @return 严格早于 date 的最近一个交易日；30 天内找不到时返回 date-1（配置异常兜底）
     */
    default LocalDate previousTradingDay(LocalDate date) {
        LocalDate cursor = date.minusDays(1);
        for (int i = 0; i < 30; i++) {
            if (isTradingDay(cursor)) {
                return cursor;
            }
            cursor = cursor.minusDays(1);
        }
        return date.minusDays(1);
    }

    /**
     * 数据日期是否属于当前交易日（V6.47）：今天必须是交易日，且数据日期=今天。
     * 当日盈亏/当日涨跌幅类字段的展示闸门——非交易日、或某基金今天的数据尚未同步过来时，
     * 用前一个交易日的数据算"当日"盈亏是把旧涨跌冒充今天的，应视为"未知"（前端显示横杠）。
     *
     * @param dataDate 行情/快照的数据日期（null 视为不满足）
     * @return true=可以展示当日口径数值
     */
    default boolean isCurrentTradingDate(LocalDate dataDate) {
        return dataDate != null && isTradingDay(LocalDate.now()) && dataDate.equals(LocalDate.now());
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
