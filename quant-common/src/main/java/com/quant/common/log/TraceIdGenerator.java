package com.quant.common.log;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * traceId 生成器（技术文档 14.2）：
 * 请求类：32 位压缩 UUID；定时任务类：job:{任务名}:{yyyyMMdd}:{当日序号}
 */
public final class TraceIdGenerator {

    /** 任务序号日期格式 */
    private static final DateTimeFormatter COMPACT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 任务级自增序号（进程内） */
    private static final AtomicInteger JOB_SEQ = new AtomicInteger(0);

    private TraceIdGenerator() {
    }

    /** 生成请求类 traceId（32 位无连字符 UUID） */
    public static String next() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** 生成任务类 traceId：job:{任务名}:{yyyyMMdd}:{序号}，便于日志定位单次任务执行 */
    public static String nextJob(String jobName) {
        return "job:" + jobName + ":" + COMPACT.format(LocalDate.now()) + ":" + JOB_SEQ.incrementAndGet();
    }
}
