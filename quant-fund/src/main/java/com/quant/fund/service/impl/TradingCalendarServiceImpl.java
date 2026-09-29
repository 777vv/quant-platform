package com.quant.fund.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.quant.fund.entity.MarketHoliday;
import com.quant.fund.mapper.MarketHolidayMapper;
import com.quant.fund.service.TradingCalendarService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 交易日判定实现（V5.24）：周末 + 休市名单（market_holiday）→ 非交易日。
 *
 * <p>名单缺失的兜底与提醒：名单是"人工补录 + 盘面自判"两路来源，若某一年份一条记录都没有
 * （例如次年安排尚未公布、也没人补录），判定会退化为"工作日即交易日"，此时按年份告警一次
 * （每 10 分钟一轮的盘中任务不会刷屏），提醒补录 —— 不静默退化。
 */
@Service
public class TradingCalendarServiceImpl implements TradingCalendarService {

    /**
     * 名单来源：平台盘面自判（与 schema.sql 注释一致）
     */
    private static final String SOURCE_OBSERVED = "observed";

    /**
     * 盘面自判补录时的默认说明
     */
    private static final String DEFAULT_OBSERVED_NAME = "盘面自判休市";

    private static final Logger LOGGER = LoggerFactory.getLogger(TradingCalendarServiceImpl.class);

    private final MarketHolidayMapper holidayMapper;

    /**
     * 已核对过"该年份名单非空"的年份（避免高频任务反复查库；未命中名单的年份才查）
     */
    private final Set<Integer> yearChecked = ConcurrentHashMap.newKeySet();

    public TradingCalendarServiceImpl(MarketHolidayMapper holidayMapper) {
        this.holidayMapper = holidayMapper;
    }

    @Override
    public boolean isTradingDay(LocalDate date) {
        if (date == null) {
            return false;
        }
        DayOfWeek week = date.getDayOfWeek();
        // A 股周六周日一律休市（调休上班的周六日也不开市），无需查库
        if (week == DayOfWeek.SATURDAY || week == DayOfWeek.SUNDAY) {
            return false;
        }
        if (exists(date)) {
            return false;
        }
        warnIfYearMissing(date);
        return true;
    }

    @Override
    public void recordObservedHoliday(LocalDate date, String reason) {
        if (date == null || exists(date)) {
            return;
        }
        MarketHoliday row = new MarketHoliday();
        row.setHolidayDate(date);
        row.setHolidayName(reason == null || reason.isBlank() ? DEFAULT_OBSERVED_NAME : reason);
        row.setSource(SOURCE_OBSERVED);
        try {
            holidayMapper.insert(row);
            LOGGER.info("休市名单补录（盘面自判）: {} {}", date, row.getHolidayName());
        } catch (DuplicateKeyException e) {
            // 并发插入同一日期：唯一键拦下即可，语义上等价于"已记录"
            LOGGER.error("休市名单已存在（并发补录）: {}", date, e);
        }
    }

    @Override
    public List<MarketHoliday> listHolidays(LocalDate from, LocalDate to) {
        return holidayMapper.selectList(new LambdaQueryWrapper<MarketHoliday>()
                .ge(from != null, MarketHoliday::getHolidayDate, from)
                .le(to != null, MarketHoliday::getHolidayDate, to)
                .orderByAsc(MarketHoliday::getHolidayDate));
    }

    /**
     * 该日期是否在休市名单里
     */
    private boolean exists(LocalDate date) {
        Long count = holidayMapper.selectCount(new LambdaQueryWrapper<MarketHoliday>()
                .eq(MarketHoliday::getHolidayDate, date));
        return count != null && count > 0;
    }

    /**
     * 名单整年缺失时告警一次：判定退化为"工作日即交易日"，节假日会误触发定时任务，
     * 需要补录（见 schema.sql 里 market_holiday 的维护说明）。
     */
    private void warnIfYearMissing(LocalDate date) {
        int year = date.getYear();
        if (!yearChecked.add(year)) {
            return;
        }
        Long count = holidayMapper.selectCount(new LambdaQueryWrapper<MarketHoliday>()
                .ge(MarketHoliday::getHolidayDate, LocalDate.of(year, 1, 1))
                .le(MarketHoliday::getHolidayDate, LocalDate.of(year, 12, 31)));
        if (count == null || count == 0) {
            LOGGER.warn("{} 年休市名单为空：交易日判定退化为「工作日即交易日」，节假日可能误触发定时任务，"
                    + "请补录 market_holiday 表（申报安排公布后一年补一次）", year);
        }
    }
}
