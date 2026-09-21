package com.quant.ai.dto;

import java.math.BigDecimal;

import lombok.Data;

/**
 * 一次 AI 调用的用量记录（由对话/自检路径填充后交给用量服务落库）。
 *
 * <p>用可变对象而非 record：流式路径的 token 是**边收边累加**的，
 * 落库前需要不断更新字段（record 每次都要重建，反而绕）。
 */
@Data
public class AiUsageRecord {

    /** 用途：见 {@link com.quant.ai.enums.AiUsageBizEnum}（CHAT/HEALTH/GUARD） */
    private String biz;

    /** 会话 ID（无会话的自检传 null） */
    private String sessionId;

    /** 本次咨询的上游请求次数（工具调用会多轮；至少 1） */
    private int rounds = 1;

    /** 输入 token */
    private int promptTokens;

    /** 输入中命中缓存的 token（单独计价） */
    private int cachedTokens;

    /** 输出 token */
    private int completionTokens;

    /** true = 上游未回 usage，token 由字符估算（前端会标注「估算」） */
    private boolean estimated;

    /** 提问字数 */
    private int questionChars;

    /** 回答字数 */
    private int answerChars;

    /** 耗时（毫秒；无耗时概念时传 null） */
    private Integer durationMs;

    /** 1 成功 / 0 失败 */
    private int status = 1;

    /** 失败原因（status=0 时填写） */
    private String errorMsg;

    /** 模型名覆盖（自检用被探测的模型；为空则取当前配置） */
    private String model;

    /** 厂商代码覆盖（为空则取当前配置） */
    private String provider;

    /**
     * 计算出的费用（元）。
     *
     * <p>**不是入参**：由 {@code AiUsageService.record(...)} 按当前单价算好后**回填**，
     * 供调用方在结束帧里即时展示（前端显示"本次 N token ≈ ¥X"）；写库失败时为 null。
     */
    private BigDecimal cost;
}
