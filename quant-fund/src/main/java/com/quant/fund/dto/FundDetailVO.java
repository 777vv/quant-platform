package com.quant.fund.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 基金详情（档案 + 最新行情）
 */
public record FundDetailVO(
        /** 基金代码 */
        String fundCode,
        /** 基金简称 */
        String fundName,
        /** 1=场内ETF 2=场外指数基金 */
        Integer fundType,
        /** 类型展示名 */
        String fundTypeDesc,
        /** 交易所市场 SH/SZ */
        String market,
        /** 跟踪指数代码 */
        String indexCode,
        /** 跟踪指数名称 */
        String indexName,
        /** 成立日期 */
        LocalDate inceptionDate,
        /** 基金公司 */
        String fundCompany,
        /** 净资产规模（亿元） */
        BigDecimal fundScale,
        /** 规模数据截止日 */
        LocalDate fundScaleDate,
        /** 运作费率（%/年）= 管理费 + 托管费 + 销售服务费 */
        BigDecimal opFeeRate,
        /** 管理费率（%/年） */
        BigDecimal mgmtFeeRate,
        /** 托管费率（%/年） */
        BigDecimal custFeeRate,
        /** 销售服务费率（%/年） */
        BigDecimal salesFeeRate,
        /** 溢价率（%），仅场内 ETF 有值 */
        BigDecimal premiumRate,
        /** 溢价率对应的净值日 */
        LocalDate premiumDate,
        /** 最新价/净值 */
        BigDecimal lastPrice,
        /** 涨跌幅% */
        BigDecimal changePct,
        /** 行情日期 */
        LocalDate priceDate,
        /** 本地最新数据日期 */
        LocalDate lastSyncDate) {
}
