package com.quant.ai.dto;

import java.util.List;

/**
 * 可选模型厂商（前端据此渲染厂商下拉、Base URL 默认值与"已配置/当前使用"状态，V4.0）。
 *
 * @param code           厂商代码
 * @param name           厂商展示名
 * @param defaultBaseUrl 默认 OpenAI 兼容端点
 * @param freeModels     已知免费模型清单（用于列表标注）
 * @param model          该厂商已保存的模型名（未配置时为空串）
 * @param configured     该厂商是否已可对话（Token 与模型齐全）
 * @param active         该厂商是否是当前启用的厂商（对话走它）
 */
public record AiProviderVO(
        /** 厂商代码 */
        String code,
        /** 厂商展示名 */
        String name,
        /** 默认 Base URL */
        String defaultBaseUrl,
        /** 已知免费模型 */
        List<String> freeModels,
        /** 该厂商已保存的模型名（未配置为空串） */
        String model,
        /** 该厂商是否已可对话 */
        boolean configured,
        /** 是否当前启用 */
        boolean active) {
}
