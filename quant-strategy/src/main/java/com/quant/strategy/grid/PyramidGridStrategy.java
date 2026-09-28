package com.quant.strategy.grid;

import org.springframework.stereotype.Component;

/**
 * 金字塔网格策略（PYRAMID_GRID，V5.28）：**越跌买越多**的网格——每深一格的成交份额按 {@code pyramidStep}
 * 递增，越往底部仓位越重，下跌中摊低成本更快，形状像正金字塔（底部宽）。
 *
 * <p>与【倒金字塔网格】是同一套规则的两个方向：{@code pyramidStep > 0} 即本策略，
 * {@code < 0} 即倒金字塔；两者的买卖共用同一条阶梯，所以"低位买得多"对应"高位也卖得多"，网格天然配对。
 *
 * <p>推荐取向（前端表单默认值即按此给，用户可逐项自己调）：
 * <ul>
 *   <li>等差网格、格宽约 1.4%（红利/宽基这类低波动品种的波动区间口径）；</li>
 *   <li>首格 500 份、每深一格 +250 份（500/750/1000/1250…），<b>跌得越深接得越多</b>；</li>
 *   <li>{@code breakoutMode=hold}（涨破上沿保留底仓、区间不动）：均值回归品种不清仓不卖飞；</li>
 *   <li>趋势闸门默认关闭——金字塔本身就假设"跌下去会回来"，若品种是长期单边下行则不适合本策略。</li>
 * </ul>
 *
 * <p>⚠️ 风险提示：金字塔在<b>单边下跌</b>中会迅速把仓位买重（仓位越跌越大），
 * 必须靠 {@code fullShare}（满仓上限）兜底，且适合"跌下去能回来"的品种（红利、宽基指数），
 * 不适合个股或长期走弱的品种。规则细节见 {@link AbstractGridStrategy}。
 */
@Component
public class PyramidGridStrategy extends AbstractGridStrategy {

    /** 策略类型标识（strategy_config.strategy_type） */
    public static final String TYPE = "PYRAMID_GRID";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String name() {
        return "金字塔网格";
    }
}
