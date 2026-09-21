package com.quant.ai.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * AI 用量流水（ai_usage_log，V3.9）。
 *
 * <p>粒度＝**一次咨询一行**：工具调用会在一次咨询里产生多次上游请求，
 * 落库前已按上游请求累加（见 {@code AiChatServiceImpl} 的累加器），`rounds` 记录轮数。
 *
 * <p>单价三列是**写入时快照**：以后在平台配置里改单价，不会追溯改写历史费用；
 * 免费模型单价为 0，费用恒为 0。
 */
@Data
@TableName("ai_usage_log")
public class AiUsageLog {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话 ID（连通性自检等无会话时为 null） */
    private String sessionId;

    /** 用途：见 {@link com.quant.ai.enums.AiUsageBizEnum}（CHAT/HEALTH/GUARD） */
    private String biz;

    /** 厂商代码（写入时快照） */
    private String provider;

    /** 模型名（写入时快照） */
    private String model;

    /** 本次咨询的上游请求次数（工具调用会多轮） */
    private Integer rounds;

    /** 输入 token（含系统提示词与记忆窗口，故每轮都会重发） */
    private Integer promptTokens;

    /** 输入中命中提示缓存的 token（单独计价） */
    private Integer cachedTokens;

    /** 输出 token */
    private Integer completionTokens;

    /** 总 token（输入 + 输出） */
    private Integer totalTokens;

    /** 1 = 上游未回 usage，token 由字符数估算（前端会标注「估算」） */
    private Integer estimated;

    /** 输入单价（元/百万 token，快照） */
    private BigDecimal inputPrice;

    /** 缓存命中输入单价（元/百万 token，快照） */
    private BigDecimal cacheInputPrice;

    /** 输出单价（元/百万 token，快照） */
    private BigDecimal outputPrice;

    /** 估算费用（元） */
    private BigDecimal cost;

    /** 提问字数 */
    private Integer questionChars;

    /** 回答字数 */
    private Integer answerChars;

    /** 耗时（毫秒；流式为开始到结束） */
    private Integer durationMs;

    /** 1 成功 / 0 失败（失败也记一行，便于把"咨询次数"与"计费次数"对上） */
    private Integer status;

    /** 失败原因 */
    private String errorMsg;

    /** 发生时间（按自然日聚合校验额度） */
    private LocalDateTime createdAt;
}
