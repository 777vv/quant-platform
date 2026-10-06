package com.quant.fund.task;

import java.time.LocalDate;

import com.quant.common.log.JobLogs;
import com.quant.fund.service.FundMaService;
import com.quant.fund.service.TradingCalendarService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 基金日均线定时任务（V5.68）：每交易日 23:00 为全部自选基金计算 5/10/20/30/60/90/120/250 日均价
 * 并落表 fund_ma_daily（幂等，重复跑覆盖当天）。排在 15:30 日K 与 20:00 净值同步之后，数据已齐。
 */
@Component
public class FundMaDailyJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(FundMaDailyJob.class);

    private final FundMaService fundMaService;

    /** 交易日判定（铁律 12）：法定节假日里均线无新数据，不再空跑 */
    private final TradingCalendarService tradingCalendarService;

    public FundMaDailyJob(FundMaService fundMaService, TradingCalendarService tradingCalendarService) {
        this.fundMaService = fundMaService;
        this.tradingCalendarService = tradingCalendarService;
    }

    /** 每交易日 23:00 计算并落表当日均线快照（数据日=当天；幂等，重复跑覆盖） */
    @Scheduled(cron = "0 0 23 * * MON-FRI", zone = "Asia/Shanghai")
    public void computeMaDaily() {
        JobLogs.run("ma:daily", () -> {
            if (!isClosedToday("ma:daily")) {
                fundMaService.refresh();
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
