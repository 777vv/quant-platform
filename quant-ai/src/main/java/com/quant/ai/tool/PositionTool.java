package com.quant.ai.tool;

import java.math.BigDecimal;
import java.util.List;

import com.quant.fund.dto.AssetSummaryVO;
import com.quant.fund.dto.HoldingVO;
import com.quant.fund.dto.SeriesPoint;
import com.quant.fund.enums.FundTypeEnum;
import com.quant.fund.service.FundQueryService;
import com.quant.fund.service.ProfitStatsService;
import com.quant.fund.service.TradeService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 工具四：我的持仓与资产（FR6，M5-03）。
 * 数据来自交易流水重算出的持仓表与收益统计引擎，与持仓页、仪表盘完全同源。
 */
@Component
public class PositionTool {

    /** 单次返回的持仓明细上限 */
    private static final int MAX_ROWS = 20;

    private final TradeService tradeService;

    private final ProfitStatsService profitStatsService;

    private final FundQueryService fundQueryService;

    public PositionTool(TradeService tradeService, ProfitStatsService profitStatsService,
                        FundQueryService fundQueryService) {
        this.tradeService = tradeService;
        this.profitStatsService = profitStatsService;
        this.fundQueryService = fundQueryService;
    }

    /**
     * 查询全部持仓明细。
     *
     * @return 逐行列出份额、摊薄成本、市值、浮动与已实现盈亏；无持仓时返回提示
     */
    @Tool(name = "getMyPositions", description = "查询当前全部持仓明细：持有份额、摊薄成本价、最新价、持仓市值、当日盈亏、浮动盈亏、已实现盈亏。用户问\"我持有什么/我的持仓/赚了多少\"时使用。无需参数。")
    public String getMyPositions() {
        List<HoldingVO> holdings = tradeService.holdings();
        if (holdings.isEmpty()) {
            return "当前没有持仓记录（持仓由交易流水自动汇总，如需登记请在基金详情页录入流水）。";
        }
        StringBuilder builder = new StringBuilder("当前持仓 ").append(holdings.size()).append(" 只：\n");
        int rows = 0;
        for (HoldingVO holding : holdings) {
            if (rows++ >= MAX_ROWS) {
                builder.append("（其余持仓已省略）\n");
                break;
            }
            builder.append("- ").append(holding.fundName()).append("（").append(holding.fundCode()).append("）")
                    .append("：份额 ").append(holding.totalShare())
                    .append("，成本价 ").append(holding.avgCostPrice())
                    .append("，最新价 ").append(holding.lastPrice() == null ? "-" : holding.lastPrice())
                    .append("，市值 ").append(holding.marketValue() == null ? "-" : holding.marketValue())
                    .append("，当日盈亏 ").append(holding.dayPnl() == null ? "-" : holding.dayPnl())
                    .append("，浮动盈亏 ").append(holding.floatingPnl() == null ? "-" : holding.floatingPnl())
                    .append(holding.floatingPnlPct() == null ? "" : "（" + holding.floatingPnlPct() + "%）")
                    .append("，已实现 ").append(holding.realizedPnl())
                    .append("\n");
        }
        return builder.toString();
    }

    /**
     * 查询资产总览。
     *
     * @return 总市值、总成本、当日/浮动/已实现/累计盈亏与收益率、近 7 日收益
     */
    @Tool(name = "getAssetSummary", description = "查询账户资产总览：持仓总市值、总成本、当日盈亏、浮动盈亏及收益率、累计已实现盈亏、累计收益及收益率、近7日收益、持仓与自选数量。无需参数。")
    public String getAssetSummary() {
        AssetSummaryVO summary = profitStatsService.summary();
        return String.format("""
                        资产总览：
                        - 总资产：%s 元（持仓市值 %s 元 + 现金余额 %s 元）
                        - 持仓成本：%s 元（持仓 %d 只，自选池 %d 只）
                        - 浮动盈亏：%s 元%s
                        - 当日盈亏：%s 元
                        - 累计已实现盈亏：%s 元
                        - 累计收益：%s 元%s
                        - 近 7 日收益：%s 元""",
                summary.totalAssets(), summary.marketValue(), summary.cashBalance(),
                summary.totalCost(), summary.holdingCount(), summary.watchCount(),
                summary.floatingPnl(), pct(summary.floatingPnlPct()),
                summary.dayPnl(), summary.realizedPnl(),
                summary.totalPnl(), pct(summary.totalPnlPct()), summary.weekPnl());
    }

    /**
     * 查询某只持仓基金的近期走势摘要（用于回答"我持有的这只最近怎么样"）。
     *
     * @param fundCode 基金代码
     * @return 近 30 个交易日区间涨跌幅；无数据时返回提示
     */
    @Tool(name = "getHoldingTrend", description = "查询某只基金近 30 个交易日的走势摘要（区间涨跌幅与最近 5 日明细）。参数为 6 位基金代码。")
    public String getHoldingTrend(@ToolParam(description = "基金代码，如 510300") String fundCode) {
        var fund = fundQueryService.getByCodeRequired(fundCode);
        List<SeriesPoint> points = FundTypeEnum.ETF.getCode() == fund.getFundType()
                ? fundQueryService.kline(fundCode, 30) : fundQueryService.nav(fundCode, 30);
        if (points.isEmpty()) {
            return fund.getFundName() + " 近 30 天没有行情数据。";
        }
        BigDecimal first = points.get(0).close() != null ? points.get(0).close() : points.get(0).unitNav();
        SeriesPoint lastPoint = points.get(points.size() - 1);
        BigDecimal last = lastPoint.close() != null ? lastPoint.close() : lastPoint.unitNav();
        String change = first == null || last == null || first.compareTo(BigDecimal.ZERO) == 0 ? "-"
                : last.subtract(first).multiply(BigDecimal.valueOf(100))
                        .divide(first, 2, java.math.RoundingMode.HALF_UP).toPlainString() + "%";
        return String.format("%s（%s）近 %d 个交易日：%s 至 %s，最新 %s，区间涨跌幅 %s。",
                fund.getFundName(), fundCode, points.size(), points.get(0).date(), lastPoint.date(), last, change);
    }

    /** 百分比展示（缺失显示为空串） */
    private String pct(BigDecimal value) {
        return value == null ? "" : "（" + value + "%）";
    }
}
