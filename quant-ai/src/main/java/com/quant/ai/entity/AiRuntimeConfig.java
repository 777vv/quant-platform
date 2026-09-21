package com.quant.ai.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * AI 运行时配置（ai_runtime_config，单行，V4.0）。
 *
 * <p>装两类"全局"状态：**当前启用的厂商**（指向 ai_model_config 里的一行）与**每日额度**。
 * 额度之所以不跟厂商走：它是"这个平台每天最多花多少"的运营政策，切厂商不该把它改掉。
 */
@Data
@TableName("ai_runtime_config")
public class AiRuntimeConfig {

    /** 固定主键（单行配置） */
    @TableId(type = IdType.INPUT)
    private Long id;

    /** 当前启用的厂商代码（对应 ai_model_config.provider；为空表示尚未配置） */
    private String activeProvider;

    /** 每日 token 上限（0 或 null = 不限制；按自然日聚合校验） */
    private Integer dailyTokenLimit;

    /** 每日费用上限（元；0 或 null = 不限制） */
    private BigDecimal dailyCostLimit;

    /** 预警百分比（用量达到该比例时提示；0 = 不预警） */
    private Integer warnPercent;

    /** 最近修改时间 */
    private LocalDateTime updatedAt;
}
