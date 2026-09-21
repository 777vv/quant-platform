package com.quant.ai.dto;

/**
 * 可选的模型项（拉取厂商 /models 得到）。
 *
 * @param id   模型名
 * @param free 是否属于内置的已知免费清单（仅供参考，以厂商官网为准）
 */
public record AiModelOptionVO(
        /** 模型名 */
        String id,
        /** 是否标注为免费 */
        boolean free) {
}
