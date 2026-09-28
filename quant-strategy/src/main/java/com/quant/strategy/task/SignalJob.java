package com.quant.strategy.task;

import java.time.LocalDate;
import java.util.List;

import com.quant.common.log.JobLogs;
import com.quant.common.util.LockUtils;
import com.quant.fund.service.TradingCalendarService;
import com.quant.strategy.entity.SignalRecord;
import com.quant.strategy.notify.NotifyService;
import com.quant.strategy.service.SignalService;
import com.quant.strategy.notify.WeComNotifyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 策略信号定时任务（FR5，M4-03）：
 * 每交易日 09:00（盘前）计算全部启用策略信号并触发邮件摘要（用户口径 V5.3：信号是交易日的、
 * 早上 9 点执行，由 21:00 改来）。信号基于上一交易日收盘数据生成（9 点时当天 bar 尚不存在），
 * 信号日期即上一交易日。
 *
 * <p>交易日闸门（V5.24）：cron 只能表达"周一到周五"，遇到工作日里的法定节假日（如 2026-09-25 中秋节）
 * 会照常执行——那时库里最新 bar 仍是节前最后一个交易日，于是会在休市日推出一条"上一交易日"的信号；
 * 而节后第一个开市的早上，反而因为没有新 bar 而不再推送（该信号的已通知标记已置位）。
 * 因此这里先按 {@link TradingCalendarService} 判定今天是不是交易日，非交易日直接跳过（不计算、不发邮件、不发微信）。
 *
 * <p>时区：cron 显式钉死 Asia/Shanghai —— @Scheduled 默认取 JVM 默认时区，部署到 UTC 机器上会变成
 * 北京时间 17:00 执行，与本机（+08:00）表现不一致。
 *
 * <p>Redisson 锁防止多实例/人工手动触发并发重复计算。
 */
@Component
public class SignalJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(SignalJob.class);

    /** 任务互斥锁键 */
    private static final String LOCK_KEY = "job:signal";

    /** 锁等待秒数（手动触发重算时最多等待上一轮完成） */
    private static final int LOCK_WAIT_SECONDS = 30;

    private final SignalService signalService;

    private final NotifyService notifyService;

    /** 微信通知（V5.20）：与邮件并行推送，互不影响 */
    private final WeComNotifyService wecomNotifyService;

    /** 交易日判定（V5.24）：非交易日（周末/法定节假日）整轮跳过 */
    private final TradingCalendarService tradingCalendarService;

    private final LockUtils lockUtils;

    public SignalJob(SignalService signalService, NotifyService notifyService,
                     WeComNotifyService wecomNotifyService, TradingCalendarService tradingCalendarService,
                     LockUtils lockUtils) {
        this.signalService = signalService;
        this.notifyService = notifyService;
        this.wecomNotifyService = wecomNotifyService;
        this.tradingCalendarService = tradingCalendarService;
        this.lockUtils = lockUtils;
    }

    /**
     * 每交易日 09:00（盘前，北京时间）信号计算 + 邮件/微信摘要。
     * 统一走 {@link JobLogs#run}：打印「开始执行 / 执行结束（耗时）」+ 任务级 traceId 串联本轮日志，
     * 失败由模板按铁律 13 记 error + 完整堆栈。
     */
    @Scheduled(cron = "0 0 9 * * MON-FRI", zone = "Asia/Shanghai")
    public void generateSignals() {
        JobLogs.run("signal", () -> {
            if (!tradingCalendarService.isTradingDay(LocalDate.now())) {
                LOGGER.info("今日（{}）非交易日（周末或休市名单），信号任务跳过：不计算、不发邮件、不发微信", LocalDate.now());
                return;
            }
            lockUtils.runWithLock(LOCK_KEY, LOCK_WAIT_SECONDS, () -> {
                List<SignalRecord> signals = signalService.generateAll();
                notifyService.sendSignalDigest(signals);
                // 微信通道（V5.20/V5.21）：与邮件并行、互不影响；内部自带 2 个交易日冷却
                wecomNotifyService.sendSignalDigest(signals);
            });
        });
    }
}
