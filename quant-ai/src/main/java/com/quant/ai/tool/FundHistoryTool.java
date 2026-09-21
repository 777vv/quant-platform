package com.quant.ai.tool;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

import com.quant.common.exception.BizException;
import com.quant.fund.dto.SeriesPoint;
import com.quant.fund.dto.ValuationSeriesVO;
import com.quant.fund.enums.FundTypeEnum;
import com.quant.fund.service.FundQueryService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 工具三：基金历史数据与估值（FR6，M5-03）。
 * 提供区间涨跌幅、区间高低点与估值百分位，避免把大段行情序列塞进上下文浪费 token。
 */
@Component
public class FundHistoryTool {

    /** 区间摘要中展示的最近明细条数 */
    private static final int RECENT_ROWS = 5;

    /** 估值百分位回看年数（与策略引擎默认口径一致） */
    private static final int PERCENTILE_WINDOW_YEARS = 10;

    private final FundQueryService fundQueryService;

    public FundHistoryTool(FundQueryService fundQueryService) {
        this.fundQueryService = fundQueryService;
    }

    /**
     * 查询基金近 N 天的历史行情摘要。
     *
     * @param fundCode 基金代码
     * @param days     回看自然日天数（如 30、90、365）
     * @return 区间首末日期、涨跌幅、区间高低与最近明细；无数据时返回提示
     */
    @Tool(name = "getFundHistory", description = "查询某只指数基金近 N 天的历史行情摘要：区间首末日期、区间涨跌幅、最高最低值以及最近几日的明细。days 为回看天数，如 30、90、365。")
    public String getFundHistory(@ToolParam(description = "基金代码，如 510300") String fundCode,
                                 @ToolParam(description = "回看天数，如 30/90/365") int days) {
        try {
            var fund = fundQueryService.getByCodeRequired(fundCode);
            int range = Math.max(1, days);
            List<SeriesPoint> points = FundTypeEnum.ETF.getCode() == fund.getFundType()
                    ? fundQueryService.kline(fundCode, range) : fundQueryService.nav(fundCode, range);
            if (points.isEmpty()) {
                return fund.getFundName() + "（" + fundCode + "）近 " + range + " 天没有行情数据。";
            }
            BigDecimal first = priceOf(points.get(0));
            BigDecimal last = priceOf(points.get(points.size() - 1));
            BigDecimal high = points.stream().map(this::priceOf).filter(Objects::nonNull)
                    .max(BigDecimal::compareTo).orElse(null);
            BigDecimal low = points.stream().map(this::priceOf).filter(Objects::nonNull)
                    .min(BigDecimal::compareTo).orElse(null);
            StringBuilder builder = new StringBuilder();
            builder.append(fund.getFundName()).append("（").append(fundCode).append("）近 ")
                    .append(points.size()).append(" 个交易日：\n")
                    .append("- 区间：").append(points.get(0).date()).append(" ~ ")
                    .append(points.get(points.size() - 1).date()).append("\n")
                    .append("- 区间涨跌幅：").append(changePct(first, last)).append("%\n")
                    .append("- 区间最高/最低：").append(high).append(" / ").append(low).append("\n")
                    .append("- 最近明细（日期 收盘/净值）：\n");
            for (int i = Math.max(0, points.size() - RECENT_ROWS); i < points.size(); i++) {
                builder.append("  ").append(points.get(i).date()).append("  ").append(priceOf(points.get(i)))
                        .append("\n");
            }
            return builder.toString();
        } catch (BizException e) {
            return "查询失败：" + e.getMessage();
        }
    }

    /**
     * 查询基金跟踪指数的估值与历史百分位。
     *
     * @param fundCode 基金代码
     * @return 最新 PE 与近 10 年百分位；无跟踪指数或估值数据不足时说明原因
     */
    @Tool(name = "getFundValuation", description = "查询某只指数基金跟踪指数的最新市盈率(PE)与近十年估值百分位，用于判断高估/低估。"
            + "当用户问\"估值贵不贵/便宜吗/高估还是低估/PE 多少/百分位多少\"时，必须先调用本工具取数，严禁凭记忆给出 PE 或百分位数字。"
            + "仅覆盖有估值数据源的指数（中证/上证系列），深市指数可能无数据。")
    public String getFundValuation(@ToolParam(description = "基金代码，如 510300") String fundCode) {
        try {
            ValuationSeriesVO valuation = fundQueryService.valuation(fundCode, PERCENTILE_WINDOW_YEARS * 365);
            if (!valuation.hasData()) {
                return "暂时查不到该基金跟踪指数的估值数据"
                        + (valuation.indexCode() == null ? "（该基金未识别到跟踪指数）"
                        : "（指数 " + valuation.indexCode() + " 暂无可用估值来源）") + "。";
            }
            return String.format("%s（指数 %s）最新 %s = %s，近 %d 年估值百分位 %s%%（百分位越低代表相对历史越便宜）。",
                    fundCode, valuation.indexName(), valuation.metric(), valuation.latestPe(),
                    PERCENTILE_WINDOW_YEARS,
                    valuation.currentPercentile() == null ? "-" : valuation.currentPercentile());
        } catch (BizException e) {
            return "查询失败：" + e.getMessage();
        }
    }

    /** 取序列点的价格（ETF 用收盘价，场外用单位净值） */
    private BigDecimal priceOf(SeriesPoint point) {
        return point.close() != null ? point.close() : point.unitNav();
    }

    /** 区间涨跌幅（%），首值为 0 或缺失时返回 "-" */
    private String changePct(BigDecimal first, BigDecimal last) {
        if (first == null || last == null || first.compareTo(BigDecimal.ZERO) == 0) {
            return "-";
        }
        return last.subtract(first).multiply(BigDecimal.valueOf(100))
                .divide(first, 2, RoundingMode.HALF_UP).toPlainString();
    }
}
