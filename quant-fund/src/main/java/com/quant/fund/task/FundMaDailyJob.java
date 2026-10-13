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

    /**
     * 每交易日 07:30 快照补跑（V6.44，幂等）：场外基金 T 日净值多数在 T 日 20:00 之后才发布——
     * 前一晚 23:00 的快照常常拿不到场外的最新数据日（10-12 实证：ETF 落 10-12、场外落在 10-09），
     * 而 07:00 的净值补拉已把场外净值补齐。07:30 再跑一次快照，让每个交易日上午的快照都是
     * "全池各基金到 T-1 日"的完整状态，下游 10:00 的均线信号判定不再受 23:00 那次是否完整影响。
     * 纯本地计算（只读 K 线/净值表），与 23:00 主跑同幂等语义。
     */
    @Scheduled(cron = "0 30 7 * * MON-FRI", zone = "Asia/Shanghai")
    public void computeMaDailyMorning() {
        JobLogs.run("ma:daily:am", () -> {
            if (!isClosedToday("ma:daily:am")) {
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
