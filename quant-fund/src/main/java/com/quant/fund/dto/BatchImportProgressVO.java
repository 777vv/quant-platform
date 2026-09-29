package com.quant.fund.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 批量导入任务进度（V5.41）：Redis 存储（2h 过期），前端轮询渲染进度条与失败清单。
 * 状态机：RUNNING（含自动暂停等待封堵窗口，用 message 提示）→ DONE / FAILED（整体失败，如锁被占用）。
 */
public record BatchImportProgressVO(
        /** 任务ID（前端轮询凭据） */
        String taskId,
        /** RUNNING=运行中 DONE=全部完成 FAILED=整体失败（如锁被占用/非法入参） */
        String status,
        /** 当前状态描述（如"疑似进入数据源封堵窗口，暂停90秒后继续"） */
        String message,
        /** 待导入总数 */
        int total,
        /** 已处理只数（成功+失败） */
        int done,
        /** 成功导入只数 */
        int success,
        /** 失败只数 */
        int failed,
        /** 正在导入的基金代码 */
        String currentCode,
        /** 正在导入的基金名称 */
        String currentName,
        /** 失败清单（只级失败不中断批量，结束后可一键重试） */
        List<FailItem> failures,
        /** 任务开始时间 */
        LocalDateTime startedAt,
        /** 任务结束时间（未结束为 null） */
        LocalDateTime finishedAt) {

    public static final String RUNNING = "RUNNING";

    public static final String DONE = "DONE";

    public static final String FAILED = "FAILED";

    /** 单只导入失败项 */
    public record FailItem(
            /** 基金代码 */
            String code,
            /** 基金名称（校验失败可能取不到，为空串） */
            String name,
            /** 失败原因（数据源封堵/不支持/非指数基金等） */
            String reason) {
    }
}
