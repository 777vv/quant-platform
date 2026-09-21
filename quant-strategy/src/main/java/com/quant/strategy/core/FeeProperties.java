package com.quant.strategy.core;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 回测费率与参数配置（quant.fee.* / 文档 11 节默认值）
 */
@ConfigurationProperties(prefix = "quant.fee")
public class FeeProperties {

    /** ETF 佣金率（万2.5） */
    private double etfCommission = 0.00025;

    /** ETF 最低佣金（元） */
    private double etfMinCommission = 5;

    /** 场外申购费率 */
    private double otcPurchase = 0.0015;

    /** 场外赎回费率 */
    private double otcRedeem = 0.005;

    /** 夏普比率无风险利率 */
    private double riskFreeRate = 0.02;

    public double getEtfCommission() {
        return etfCommission;
    }

    public void setEtfCommission(double etfCommission) {
        this.etfCommission = etfCommission;
    }

    public double getEtfMinCommission() {
        return etfMinCommission;
    }

    public void setEtfMinCommission(double etfMinCommission) {
        this.etfMinCommission = etfMinCommission;
    }

    public double getOtcPurchase() {
        return otcPurchase;
    }

    public void setOtcPurchase(double otcPurchase) {
        this.otcPurchase = otcPurchase;
    }

    public double getOtcRedeem() {
        return otcRedeem;
    }

    public void setOtcRedeem(double otcRedeem) {
        this.otcRedeem = otcRedeem;
    }

    public double getRiskFreeRate() {
        return riskFreeRate;
    }

    public void setRiskFreeRate(double riskFreeRate) {
        this.riskFreeRate = riskFreeRate;
    }
}
