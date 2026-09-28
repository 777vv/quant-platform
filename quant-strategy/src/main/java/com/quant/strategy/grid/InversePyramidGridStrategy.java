package com.quant.strategy.grid;

import org.springframework.stereotype.Component;

/**
 * 倒金字塔网格策略（INV_PYRAMID_GRID，V5.28）：**越跌买越少**的网格——每深一格的成交份额按 {@code pyramidStep}
 * 递减，浅回撤时接得多、深跌时接得少，形状与正金字塔相反（顶部宽、底部窄）。
 *
 * <p>设计意图：把子弹优先留给"浅回调"、深跌时保持克制。适合
 * <b>长期趋势向上、但深跌难以判断底</b>的品种（纳指这类跨境指数），
 * 与【金字塔网格】相反——金字塔相信"深跌是机会"，倒金字塔相信"深跌是趋势变化"。
 *
 * <p>推荐取向（前端表单默认值即按此给，用户可逐项自己调）：
 * <ul>
 *   <li>等比网格、格宽约 3.4%（高波动跨境品种口径）；</li>
 *   <li>首格 1500 份、每深一格 −100 份（1500/1400/1300…递减，最深处仍有 600 份，不会归零）；</li>
 *   <li>{@code breakoutMode=shift}（涨破上沿区间整体上移），配合 {@code trendMaDays=60}（跌破均线禁买）
 *       与 {@code premiumBuyMaxPct=3}（高溢价禁买）——这三个闸门是跨境 ETF 的主要保护。</li>
 * </ul>
 *
 * <p>⚠️ 风险提示：倒金字塔的仓位重心在上方，<b>浅回调买得多</b>，若价格一直阴跌不反弹，
 * 会先重后轻地套在上方；务必设 {@code fullShare} 上限并用趋势闸门约束。
 * 规则细节见 {@link AbstractGridStrategy}。
 */
@Component
public class InversePyramidGridStrategy extends AbstractGridStrategy {

    /** 策略类型标识（strategy_config.strategy_type） */
    public static final String TYPE = "INV_PYRAMID_GRID";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String name() {
        return "倒金字塔网格";
    }
}
