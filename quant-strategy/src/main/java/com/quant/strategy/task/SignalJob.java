package com.quant.strategy.task;

import java.util.List;

import com.quant.common.log.TraceIdGenerator;
import com.quant.common.util.LockUtils;
import com.quant.strategy.entity.SignalRecord;
import com.quant.strategy.notify.NotifyService;
import com.quant.strategy.service.SignalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 策略信号定时任务（FR5，M4-03）：
 * 每交易日 21:00（净值 20:00、估值 20:30 同步完成后）计算全部启用策略信号，
 * 并触发邮件摘要通知；Redisson 锁防止多实例/人工手动触发并发重复计算。
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

    private final LockUtils lockUtils;

    public SignalJob(SignalService signalService, NotifyService notifyService, LockUtils lockUtils) {
        this.signalService = signalService;
        this.notifyService = notifyService;
        this.lockUtils = lockUtils;
    }

    /** 每交易日 21:00 信号计算 + 邮件摘要 */
    @Scheduled(cron = "0 0 21 * * MON-FRI")
    public void generateSignals() {
        MDC.put("traceId", TraceIdGenerator.nextJob("signal"));
        try {
            lockUtils.runWithLock(LOCK_KEY, LOCK_WAIT_SECONDS, () -> {
                List<SignalRecord> signals = signalService.generateAll();
                notifyService.sendSignalDigest(signals);
            });
        } catch (Exception e) {
            LOGGER.error("信号计算任务失败: {}", e.getMessage(), e);
        } finally {
            MDC.clear();
        }
    }
}
