package com.quant.fund.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 市场信号总览（V5.39）：一次返回自选池全部基金的三块指标，
 * 供「市场信号」页三个页签渲染（涨跌榜 / 估值红绿灯 / 技术面与溢价）。
 *
 * <p>全部指标由库内数据计算（ETF 用前复权收盘、场外用复权净值），不落数据源请求；
 * 页面打开即算，数据新鲜度跟随各同步任务（15:30 日K / 20:30 估值 / 盘中档案）。
 */
public record MarketSignalVO(
        /** 本次计算使用的 PE 分位窗口（3y/5y/10y/all，回显给前端核对） */
        String peWindow,
        /** 每只基金一行 */
        List<Row> rows) {

    /**
     * 单只基金的市场信号行。
     * 数值口径：涨跌幅与回撤均为百分数（正数）；「—」一律用 null 表达，由前端格式化。
     */
    public record Row(
            /** 基金代码 */
            String fundCode,
            /** 基金名称 */
            String fundName,
            /** 1=场内ETF 2=场外指数基金（决定是否有溢价率） */
            Integer fundType,
            /** 基金标签名列表（前端标签筛选用，按 sortNo 升序） */
            List<String> tags,

            /** 最新收盘价/复权净值（与涨跌幅同口径） */
            BigDecimal lastClose,
            /** 价格数据截至日（K线最新交易日 / 净值最新日） */
            LocalDate lastDate,

            /** 近 5 个交易日涨跌幅%（短期反转最强窗口：领涨≠能追） */
            BigDecimal chg5d,
            /** 近 10 个交易日涨跌幅%（短期反转区：领跌=超跌关注） */
            BigDecimal chg10d,
            /** 近 20 个交易日涨跌幅%（约一个月，短中期过渡） */
            BigDecimal chg20d,
            /** 近 30 个交易日涨跌幅%（中期动量） */
            BigDecimal chg30d,
            /** 近 60 个交易日涨跌幅%（约一个季度，中期动量） */
            BigDecimal chg60d,
            /** 近 90 个交易日涨跌幅%（中期动量） */
            BigDecimal chg90d,
            /** 近 120 个交易日涨跌幅%（约半年，中期动量） */
            BigDecimal chg120d,
            /** 近 250 个交易日涨跌幅%（52 周位置，历史不足显示 null） */
            BigDecimal chg250d,

            /** 现价是否在 MA20 上方（1=上 0=下，均线不足为 null） */
            Integer aboveMa20,
            /** 现价是否在 MA60 上方（1=上 0=下，均线不足为 null） */
            Integer aboveMa60,
            /** 现价是否在 MA200 上方（1=上 0=下，历史不足 200 根为 null） */
            Integer aboveMa200,
            /** 趋势汇总：三线全上=多头排列、全下=空头排列、其余=震荡；任一均线缺失为 null */
            String maSummary,

            /** 当前回撤深度%（相对历史最高收盘的最大跌幅，0=处于高点） */
            BigDecimal drawdownPct,
            /** 回撤分位（0-100）：历史全部交易日回撤中小于当前回撤的占比，越大越极端 */
            BigDecimal drawdownPctile,

            /** 跟踪指数名称（未匹配为 null，估值整块为 null） */
            String indexName,
            /** 跟踪指数最新 PE */
            BigDecimal pe,
            /** PE 百分位（0-100，按请求窗口计算，与基金池/估值页签同口径） */
            BigDecimal pePctile,
            /** PE 数据截至日 */
            LocalDate peDate,
            /** 分位窗口内的 PE 样本天数（判断分位可信度） */
            Integer peSamples,

            /** 股息率 TTM%（仅展示，不参与灯色判断） */
            BigDecimal dyTtm,

            /** 溢价率%（仅场内 ETF；=（收盘价−净值）/净值，场外为 null） */
            BigDecimal premiumPct,
            /** 溢价率对应净值日 */
            LocalDate premiumDate) {
    }
}
