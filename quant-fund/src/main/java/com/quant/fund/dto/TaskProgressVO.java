package com.quant.fund.dto;

/**
 * 异步任务进度（导入/回测/手动同步），Redis 存储 1h 过期
 */
public record TaskProgressVO(
        /** 任务ID（前端轮询凭据） */
        String taskId,
        /** RUNNING=运行中 DONE=成功 FAILED=失败 */
        String status,
        /** 当前步骤/最终结果描述 */
        String step,
        /** 预计总数（0=未知） */
        int total,
        /** 已处理条数 */
        int imported,
        /** 失败原因（FAILED 时） */
        String message) {

    public static final String RUNNING = "RUNNING";

    public static final String DONE = "DONE";

    public static final String FAILED = "FAILED";
}
