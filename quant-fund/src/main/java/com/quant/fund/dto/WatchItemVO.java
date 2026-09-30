package com.quant.fund.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 自选基金列表条目
 */
public record WatchItemVO(
        /** 基金代码 */
        String fundCode,
        /** 基金简称 */
        String fundName,
        /** 1=场内ETF 2=场外指数基金 */
        Integer fundType,
        /** 类型展示名（ETF/场外指数基金） */
        String fundTypeDesc,
        /** 跟踪指数名称 */
        String indexName,
        /** 跟踪指数代码（如 000922），未匹配到为 null */
        String indexCode,
        /** 最新价/净值 */
        BigDecimal lastPrice,
        /** 涨跌幅% */
        BigDecimal changePct,
        /** 跟踪指数近10年 PE 百分位（无估值数据为 null） */
        BigDecimal valuationPercentile,
        /** 本地最新数据日期 */
        LocalDate lastSyncDate,
        /** 最后同步动作时间（含时分秒，V5.49；老数据无此值时为 null） */
        LocalDateTime lastSyncAt,
        /** 净资产规模（亿元），未取到为 null */
        BigDecimal fundScale,
        /** 规模数据截止日 */
        LocalDate fundScaleDate,
        /** 运作费率（%/年）= 管理费 + 托管费 + 销售服务费，任一为 null 按 0 计；全为 null 时为 null */
        BigDecimal opFeeRate,
        /** 管理费率（%/年） */
        BigDecimal mgmtFeeRate,
        /** 托管费率（%/年） */
        BigDecimal custFeeRate,
        /** 销售服务费率（%/年） */
        BigDecimal salesFeeRate,
        /** 溢价率（%）=（当日收盘价 − 当日单位净值）/ 当日单位净值；仅场内 ETF 有值 */
        BigDecimal premiumRate,
        /** 溢价率对应的净值日 */
        LocalDate premiumDate,
        /** 是否持仓（该基金持有份额 &gt; 0）；列表按"持仓优先"排序 */
        boolean holding,
        /** TTM 股息率（%）= 过去 12 个月每份分红 ÷ 最新真实价格；无分红或无价格为 null */
        java.math.BigDecimal dividendYieldTtm) {
}
