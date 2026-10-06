package com.quant.strategy.task;

import java.time.LocalDate;

import com.quant.common.log.JobLogs;
import com.quant.fund.service.TradingCalendarService;
import com.quant.strategy.service.MaSignalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 均线信号定时任务（V5.70）：每交易日 10:00 从 fund_ma_daily 判定短期均线上穿/下穿长期均线
 * 并记录信号（比较的是最近两个已收盘交易日，即"盘前看昨日收盘信号"）。
 * 排在每日 23:00 的 ma:daily 均线快照任务之后，数据已齐。
 */
@Component
public class MaSignalJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(MaSignalJob.class);

    private final MaSignalService maSignalService;

    /** 交易日判定（铁律 12）：法定节假日无新快照，不空跑 */
    private final TradingCalendarService tradingCalendarService;

    public MaSignalJob(MaSignalService maSignalService, TradingCalendarService tradingCalendarService) {
        this.maSignalService = maSignalService;
        this.tradingCalendarService = tradingCalendarService;
    }

    /** 每交易日 10:00 增量判定均线交叉信号（幂等，重复跑不重复入库） */
    @Scheduled(cron = "0 0 10 * * MON-FRI", zone = "Asia/Shanghai")
    public void computeMaSignals() {
        JobLogs.run("ma-signal:daily", () -> {
            if (!isClosedToday("ma-signal:daily")) {
                int created = maSignalService.computeFromMaDaily();
                LOGGER.info("均线信号判定完成：本次新增 {} 条", created);
            }
        });
    }

    private boolean isClosedToday(String jobName) {
        if (tradingCalendarService.isTradingDay(LocalDate.now())) {
            return false;
        }
        LOGGER.info("今日（{}）非交易日（休市名单），任务[{}]跳过：不执行", LocalDate.now(), jobName);
        return true;
    }
}
