package com.quant.strategy.grid;

import org.springframework.stereotype.Component;

/**
 * 红利网格策略（DIV_GRID，V5.27）：面向红利/红利低波这类**低波动、慢牛、均值回归强**的 ETF
 * （实测近一年：515080 年化波动 13.7%、512890 12.0%）。
 *
 * <p>推荐参数取向（前端表单默认值即按此给，用户可逐项自己调）：
 * <ul>
 *   <li>等差网格、格宽约 1.5%~2%（约 2 倍日均波动），格数 8~12；</li>
 *   <li><b>底仓占比高</b>（红利主要收益来自持有，网格只是增强）；</li>
 *   <li>{@code breakoutMode=hold}：涨破上沿按格卖出后**保留底仓**，不做区间上移——
 *       慢牛品种一旦清仓就是永久卖飞；</li>
 *   <li>{@code trendMaDays=0}：红利下跌本身就是要买的时刻，默认不启用趋势闸门；</li>
 *   <li>溢价率闸门可选（红利 ETF 溢价常年在 0 附近，455080 实测 -0.05%）；</li>
 *   <li>可选估值闸门（{@code peBuyMax}/{@code peBuyMin}）：中证红利指数（000922）有 PE 数据，
 *       估值贵了少买、便宜了加倍买。</li>
 * </ul>
 *
 * <p>规则细节全部在 {@link AbstractGridStrategy}（与【纳指网格】共用同一套实现）。
 */
@Component
public class DividendGridStrategy extends AbstractGridStrategy {

    /** 策略类型标识（strategy_config.strategy_type） */
    public static final String TYPE = "DIV_GRID";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String name() {
        return "红利网格";
    }
}
