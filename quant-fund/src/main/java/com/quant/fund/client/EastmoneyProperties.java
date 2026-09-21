package com.quant.fund.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 东方财富数据源配置（quant.eastmoney.*）
 */
@ConfigurationProperties(prefix = "quant.eastmoney")
public class EastmoneyProperties {

    /** 数据源总开关（熔断用） */
    private boolean enabled = true;

    /** 相邻两次请求最小间隔（毫秒） */
    private long intervalMs = 300;

    /** 连接超时（毫秒） */
    private int connectTimeoutMs = 5000;

    /** 读取超时（毫秒） */
    private int readTimeoutMs = 10000;

    /** 失败重试次数 */
    private int retryTimes = 3;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getIntervalMs() {
        return intervalMs;
    }

    public void setIntervalMs(long intervalMs) {
        this.intervalMs = intervalMs;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public int getRetryTimes() {
        return retryTimes;
    }

    public void setRetryTimes(int retryTimes) {
        this.retryTimes = retryTimes;
    }
}
