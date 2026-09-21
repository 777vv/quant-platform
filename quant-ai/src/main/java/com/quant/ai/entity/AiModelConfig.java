package com.quant.ai.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * AI 模型配置（ai_model_config，V4.0 起为**每个厂商一行**）。
 *
 * <p>为什么按厂商各存一行：单行存储下"换厂商保存"必然覆盖上一家的 Base URL/Token/模型/单价，
 * 用户切回旧厂商就得重填一遍。现在切换厂商只是移动 {@link AiRuntimeConfig#getActiveProvider()}
 * 这个指针，各家配置都留着，互不覆盖。
 *
 * <p>单价是 **per-provider** 的（各厂价目不同）；每日额度是全局的，放在 {@link AiRuntimeConfig}。
 */
@Data
@TableName("ai_model_config")
public class AiModelConfig {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 厂商代码：ZHIPU/QWEN/DEEPSEEK/KIMI/MINIMAX（见 AiProviderEnum，唯一键） */
    private String provider;

    /** OpenAI 兼容端点（按厂商各存，如 https://api.deepseek.com/v1） */
    private String baseUrl;

    /** 模型名（按厂商各存，如 deepseek-chat） */
    private String model;

    /** API Token（按厂商各存；明文存储于本机数据库，接口返回时打码） */
    private String apiKey;

    /** 输入单价（元/百万 token；0 = 不计算费用，如免费模型） */
    private BigDecimal inputPrice;

    /** 缓存命中输入单价（元/百万 token；0 = 按输入单价计，避免命中部分漏计） */
    private BigDecimal cacheInputPrice;

    /** 输出单价（元/百万 token；0 = 不计算费用） */
    private BigDecimal outputPrice;

    /** 最近修改时间 */
    private LocalDateTime updatedAt;
}
