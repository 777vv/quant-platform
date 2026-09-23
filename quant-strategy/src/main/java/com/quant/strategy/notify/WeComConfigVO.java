package com.quant.strategy.notify;

/**
 * 微信通知配置只读视图（平台配置微信卡片；secret 不回传）。
 *
 * @param enabled    微信通知总开关
 * @param corpid     企业 ID（打码展示：保留前 4 位）
 * @param agentId    自建应用 AgentId（非敏感，原样返回）
 * @param touser     接收人（@all 或 userid 列表）
 * @param configured 四项配置（corpid/agentId/secret/touser）是否齐全
 */
public record WeComConfigVO(
        boolean enabled,
        String corpid,
        String agentId,
        String touser,
        boolean configured) {

    /** 由配置行组装视图（corpid 打码、configured = 三项关键配置是否齐全） */
    public static WeComConfigVO of(com.quant.strategy.entity.SysWecomConfig row) {
        boolean hasAll = row.getCorpid() != null && !row.getCorpid().isBlank()
                && row.getAgentId() != null && !row.getAgentId().isBlank()
                && row.getSecret() != null && !row.getSecret().isBlank();
        String corpid = row.getCorpid() == null || row.getCorpid().length() <= 4
                ? (row.getCorpid() == null ? "" : row.getCorpid())
                : row.getCorpid().substring(0, 4) + "***";
        return new WeComConfigVO(
                Integer.valueOf(1).equals(row.getEnabled()),
                corpid,
                row.getAgentId() == null ? "" : row.getAgentId(),
                row.getTouser() == null ? "@all" : row.getTouser(),
                hasAll);
    }
}
