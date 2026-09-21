package com.quant.ai.dto;

import java.math.BigDecimal;

/**
 * AI 模型配置保存请求（保存**当前选中的这个厂商**那一行，V4.0）。
 *
 * <p>语义：保存成功即把该厂商设为「当前启用厂商」——所以"选中厂商 + 保存并生效"就是切换厂商；
 * 各厂商的配置互不覆盖（每个厂商一行）。
 *
 * @param provider        厂商代码
 * @param baseUrl         OpenAI 兼容端点（留空取该厂商默认值）
 * @param model           模型名
 * @param apiKey          API Token；留空或回传打码值时表示"不修改该厂商已存的 Token"
 * @param inputPrice      该厂商输入单价（元/百万 token；0 = 不计算费用）
 * @param cacheInputPrice 该厂商缓存命中输入单价（元/百万 token；0 = 按输入单价计）
 * @param outputPrice     该厂商输出单价（元/百万 token；0 = 不计算费用）
 */
public record AiModelConfigRequest(
        /** 厂商代码 */
        String provider,
        /** OpenAI 兼容端点 */
        String baseUrl,
        /** 模型名 */
        String model,
        /** API Token（空=保持该厂商已存值） */
        String apiKey,
        /** 输入单价（元/百万 token） */
        BigDecimal inputPrice,
        /** 缓存命中输入单价（元/百万 token） */
        BigDecimal cacheInputPrice,
        /** 输出单价（元/百万 token） */
        BigDecimal outputPrice) {
}
