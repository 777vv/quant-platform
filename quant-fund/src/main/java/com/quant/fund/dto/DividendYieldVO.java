package com.quant.fund.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 分红股息率（V3.4）。
 *
 * <p>口径说明：
 * <ul>
 *   <li><b>单次分红股息率</b> = 每份分红 ÷ 除息日**真实价格**（未复权收盘价；场外为单位净值）；</li>
 *   <li><b>TTM 股息率</b> = 过去 12 个月每份分红合计 ÷ 当日真实价格，按除息日（以及"分红滚出 12 个月窗口"的日子）阶跃变化，
 *       这是红利类指数/基金报"股息率"时的默认口径。</li>
 * </ul>
 * ⚠️ 分母必须用真实价格：我们存的前复权价把历史价格压低了（等于把分红从价格里抹掉），
 * 直接拿它当分母会系统性高估历史股息率；因此 ETF 的历史股息率依赖 `fund_etf_kline.unadj_close`
 * （每日全量同步额外拉 fqt=0 写入），缺失时该区间返回 null 并由前端按"--"处理。
 *
 * @param fundCode       基金代码
 * @param fundName       基金名称
 * @param events         区间内每次分红的股息率
 * @param ttm            区间内 TTM 股息率阶跃序列（股息率% ，priceMissing=true 时元素 yieldPct 为 null）
 * @param priceAvailable 历史真实价格是否可用（false 时 events/ttm 的股息率均为 null，只有日期）
 * @param latestTtm      最新 TTM 股息率（%），无数据为 null
 * @param hint           数据缺失时的说明文案
 */
public record DividendYieldVO(
        /** 基金代码 */
        String fundCode,
        /** 基金名称 */
        String fundName,
        /** 区间内每次分红的股息率 */
        List<EventYield> events,
        /** TTM 股息率阶跃序列 */
        List<TtmPoint> ttm,
        /** 历史真实价格是否可用 */
        boolean priceAvailable,
        /** 最新 TTM 股息率（%） */
        BigDecimal latestTtm,
        /** 说明文案 */
        String hint) {

    /**
     * 单次分红股息率。
     *
     * @param date     除息日
     * @param perShare 每份分红（元）= 每 10 份派现 ÷ 10
     * @param price    除息日真实价格（未复权收盘 / 单位净值），缺失为 null
     * @param yieldPct 当日股息率（%），price 缺失为 null
     */
    public record EventYield(
            /** 除息日 */
            String date,
            /** 每份分红（元） */
            BigDecimal perShare,
            /** 除息日真实价格 */
            BigDecimal price,
            /** 当日股息率（%） */
            BigDecimal yieldPct) {
    }

    /**
     * TTM 股息率阶跃点。
     *
     * @param date     生效日期（除息日、或分红滚出 12 个月窗口之日）
     * @param yieldPct 自该日起的 TTM 股息率（%），价格缺失为 null
     */
    public record TtmPoint(
            /** 生效日期 */
            String date,
            /** TTM 股息率（%） */
            BigDecimal yieldPct) {
    }
}
