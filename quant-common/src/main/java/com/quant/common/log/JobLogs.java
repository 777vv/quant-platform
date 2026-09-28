package com.quant.common.log;

import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * 定时任务统一日志模板（V5.38，用户要求）：
 * 每次执行打印「开始执行」与「执行结束（含耗时）」，并用任务级 traceId 串联本轮全部日志——
 * 任务体内各服务打的日志都带同一个 traceId（logback 的 {@code %X{traceId}}），
 * 排查时按 traceId 一捞就是一次执行的完整链路。
 *
 * <p>失败按铁律 13 统一记 error 级 + 完整堆栈（异常作为 LOGGER 的最后一个参数），
 * 失败日志同时充当本轮的"结束"标记（开始 → 结束/失败，成对出现）；
 * 异常不再向上抛——Spring 默认的 ErrorHandler 只会打一条没有任务名的堆栈，信息量更少。
 */
public final class JobLogs {

    private static final Logger LOGGER = LoggerFactory.getLogger(JobLogs.class);

    private JobLogs() {
    }

    /** 无返回值的任务体 */
    public static void run(String jobName, Runnable task) {
        call(jobName, () -> {
            task.run();
            return null;
        });
    }

    /**
     * 有返回值的任务体（如"本轮是否发生了告警"）。
     * 失败时返回 {@code null}，调用方按需兜底。
     */
    public static <T> T call(String jobName, Supplier<T> task) {
        MDC.put(TraceIdFilter.MDC_KEY, TraceIdGenerator.nextJob(jobName));
        long startedAt = System.currentTimeMillis();
        LOGGER.info("定时任务[{}]开始执行", jobName);
        try {
            T result = task.get();
            LOGGER.info("定时任务[{}]执行结束，耗时[{}ms]", jobName, System.currentTimeMillis() - startedAt);
            return result;
        } catch (Exception e) {
            LOGGER.error("定时任务[{}]执行失败，耗时[{}ms]", jobName, System.currentTimeMillis() - startedAt, e);
            return null;
        } finally {
            // 线程池复用线程，必须清理，避免下一轮任务串号
            MDC.clear();
        }
    }
}
