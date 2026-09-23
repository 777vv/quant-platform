package com.quant.strategy.notify;

/**
 * 微信通知配置保存请求（V5.20，平台配置页微信卡）。
 *
 * <p>secret 语义与邮件授权码一致：**留空或回传打码值表示不修改已存 Secret**。
 *
 * @param enabled  微信通知总开关（null = 保持原值）
 * @param corpid   企业 ID（企业微信后台-我的企业）
 * @param agentId  自建应用 AgentId
 * @param secret   自建应用 Secret（留空/打码 = 保持原值）
 * @param touser   接收人（@all 或 userid 列表；空 = @all）
 */
public record WeComConfigRequest(
        /** 微信通知总开关（null = 保持原值） */
        Boolean enabled,
        /** 企业 ID */
        String corpid,
        /** 自建应用 AgentId */
        String agentId,
        /** 自建应用 Secret（留空/打码 = 保持原值） */
        String secret,
        /** 接收人（@all 或 userid 列表；空 = @all） */
        String touser) {
}
