package com.quant.fund.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 同步状态汇总配置（quant.sync-summary.*，M6-01 从常量外部化）。
 * 宽限天数用于吸收法定节假日：ETF 与场外净值公布节奏不同，故分别配置。
 */
@ConfigurationProperties(prefix = "quant.sync-summary")
public class SyncSummaryProperties {

    /** ETF 数据允许的滞后宽限（自然日；默认 4 天，覆盖周末与常见短假） */
    private int etfAllowedLagDays = 4;

    /** 场外净值允许的滞后宽限（自然日；默认 7 天，含 T+1 公布与节假日） */
    private int otcAllowedLagDays = 7;

    /**
     * 强制标记全部基金为滞后（默认 false）。
     * 仅供"同步异常告警邮件"的演练与验证使用——正常运行时保持 false，
     * 置 true 会让仪表盘把所有基金标红并触发告警邮件。
     */
    private boolean forceLagging = false;

    public int getEtfAllowedLagDays() {
        return etfAllowedLagDays;
    }

    public void setEtfAllowedLagDays(int etfAllowedLagDays) {
        this.etfAllowedLagDays = etfAllowedLagDays;
    }

    public int getOtcAllowedLagDays() {
        return otcAllowedLagDays;
    }

    public void setOtcAllowedLagDays(int otcAllowedLagDays) {
        this.otcAllowedLagDays = otcAllowedLagDays;
    }

    public boolean isForceLagging() {
        return forceLagging;
    }

    public void setForceLagging(boolean forceLagging) {
        this.forceLagging = forceLagging;
    }
}
