package com.quant.strategy.grid;

import org.springframework.stereotype.Component;

/**
 * 纳指网格策略（NDX_GRID，V5.27）：面向纳斯达克 100 等**高波动、强趋势、深回撤**的跨境 ETF
 * （实测近一年：513300 年化波动 23.3%、区间跨度 39.5%）。
 *
 * <p>推荐参数取向（前端表单默认值即按此给，用户可逐项自己调）：
 * <ul>
 *   <li><b>等比网格</b>、格宽约 3%~4%（约 2 倍日均波动）——格太密的话一次 -30% 级别回撤会连穿十几格；</li>
 *   <li><b>底仓占比低、满仓上限严格</b>：深回撤时"越跌越买"最容易打光子弹，必须留足；</li>
 *   <li>{@code premiumBuyMaxPct=3}：<b>跨境的头号风险是溢价</b>——实测 513300 当前溢价 +8.93%、
 *       513500 达 +10%，指数没跌但溢价一收敛就是先亏一笔，所以溢价高时禁止买入（{@code premiumStaleDays}
 *       容忍 QDII 净值公布滞后，超期视为数据过期、不拦截并提示）；</li>
 *   <li>{@code trendMaDays=60}：现价低于 60 日均线时禁止买入（跌势中不接飞刀），卖出不受限；</li>
 *   <li>{@code breakoutMode=shift}：涨破上沿后区间整体上移（移动网格）——纳指长期创新高，
 *       固定上沿会反复卖飞；移动网格只涨不跌，是"跟着趋势抬台阶"的显式语义。</li>
 * </ul>
 *
 * <p>⚠️ 回测口径：平台只存最新一天的溢价率（历史无法回补），所以回测默认**跳过溢价闸门**，
 * 结果会比实盘乐观；想模拟高溢价环境可填 {@code backtestPremiumPct}（按固定假设溢价率判断）。
 * 纳指没有指数 PE 数据源，估值闸门对它不生效（留 0 即可）。
 *
 * <p>规则细节全部在 {@link AbstractGridStrategy}（与【红利网格】共用同一套实现）。
 */
@Component
public class NasdaqGridStrategy extends AbstractGridStrategy {

    /** 策略类型标识（strategy_config.strategy_type） */
    public static final String TYPE = "NDX_GRID";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String name() {
        return "纳指网格";
    }
}
