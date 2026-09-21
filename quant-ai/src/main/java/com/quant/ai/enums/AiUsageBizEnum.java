package com.quant.ai.enums;

/**
 * AI 用量流水的用途（ai_usage_log.biz）。
 *
 * <p>分三档的目的：把「用户问了几次」「实际计费几次」分开看——
 * 护栏拒答不调用大模型（零成本），自检会真实消耗少量 token。
 */
public enum AiUsageBizEnum {

    /** 正常对话（含工具调用的多轮） */
    CHAT("对话"),

    /** 连通性自检（真实消耗 token，但不计入额度拦截，否则配好 Key 反而无法自检） */
    HEALTH("自检"),

    /** 越狱护栏拒答（入口拦下，未调用大模型，token 与费用恒为 0） */
    GUARD("护栏");

    /** 中文展示名 */
    private final String displayName;

    AiUsageBizEnum(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
