package com.quant.fund.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 仪表盘全球指数配置（quant.dashboard.indices，secid 对照经实测修正：HSTECH=124 前缀）
 */
@ConfigurationProperties(prefix = "quant.dashboard")
public class DashboardProperties {

    private List<IndexItem> indices = new ArrayList<>();

    public List<IndexItem> getIndices() {
        return indices;
    }

    public void setIndices(List<IndexItem> indices) {
        this.indices = indices;
    }

    /**
     * 单只指数配置
     */
    public static class IndexItem {

        private String secid;

        private String name;

        /** CN/HK/US/ASIA/EU */
        private String region;

        public String getSecid() {
            return secid;
        }

        public void setSecid(String secid) {
            this.secid = secid;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }
    }
}
