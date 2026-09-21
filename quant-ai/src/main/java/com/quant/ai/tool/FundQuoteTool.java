package com.quant.ai.tool;

import java.math.BigDecimal;

import com.quant.common.exception.BizException;
import com.quant.fund.dto.LastQuote;
import com.quant.fund.dto.WatchItemVO;
import com.quant.fund.entity.FundBasic;
import com.quant.fund.service.FundQueryService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 工具一：基金实时行情（FR6，M5-03）。
 * 数据取自本地库（由定时同步维护的 ETF 前复权收盘价 / 场外单位净值），
 * 与看板、基金列表同源，保证回答与页面显示一致。
 */
@Component
public class FundQuoteTool {

    private final FundQueryService fundQueryService;

    public FundQuoteTool(FundQueryService fundQueryService) {
        this.fundQueryService = fundQueryService;
    }

    /**
     * 查询单只基金最新行情。
     *
     * @param fundCode 基金代码，如 510300
     * @return 人类可读的行情文本（含价格、涨跌幅、数据日期）；基金不在自选池时返回提示
     */
    @Tool(name = "getFundQuote", description = "查询自选池中某只指数基金的最新价格与涨跌幅。ETF 返回前复权收盘价，场外基金返回单位净值。参数为 6 位基金代码，如 510300。")
    public String getFundQuote(@ToolParam(description = "基金代码，如 510300") String fundCode) {
        try {
            FundBasic fund = fundQueryService.getByCodeRequired(fundCode);
            LastQuote quote = fundQueryService.lastQuote(fund);
            if (quote == null || quote.price() == null) {
                return fund.getFundName() + "（" + fundCode + "）暂无行情数据，可能尚未完成历史导入。";
            }
            return String.format("%s（%s）最新价 %s，涨跌幅 %s%%，数据日期 %s。",
                    fund.getFundName(), fundCode, plain(quote.price()),
                    quote.changePct() == null ? "-" : plain(quote.changePct()), quote.date());
        } catch (BizException e) {
            return "查询失败：" + e.getMessage() + "（该基金可能未加入自选池）";
        }
    }

    /**
     * 查询自选池全部基金的行情概览。
     *
     * @return 逐行列出代码、名称、最新价与涨跌幅；池内无基金时返回提示
     */
    @Tool(name = "listWatchlistQuotes", description = "查询自选池中全部指数基金的最新价与涨跌幅概览，用于对比多只基金行情。无需参数。")
    public String listWatchlistQuotes() {
        var items = fundQueryService.watchlist();
        if (items.isEmpty()) {
            return "自选池当前没有基金，请先在平台的基金池页面添加。";
        }
        StringBuilder builder = new StringBuilder("自选池共 ").append(items.size()).append(" 只基金：\n");
        for (WatchItemVO item : items) {
            builder.append("- ").append(item.fundName()).append("（").append(item.fundCode()).append("）")
                    .append("最新价 ").append(plain(item.lastPrice()))
                    .append("，涨跌幅 ").append(item.changePct() == null ? "-" : plain(item.changePct()) + "%")
                    .append("，跟踪 ").append(item.indexName() == null ? "未识别" : item.indexName())
                    .append("，估值百分位 ")
                    .append(item.valuationPercentile() == null ? "无数据" : plain(item.valuationPercentile()) + "%")
                    .append("，数据日期 ").append(item.lastSyncDate() == null ? "-" : item.lastSyncDate())
                    .append("\n");
        }
        return builder.toString();
    }

    /** 统一数值展示：去掉无意义的尾随零（2 位小数） */
    private String plain(BigDecimal value) {
        return value == null ? "-" : value.stripTrailingZeros().toPlainString();
    }
}
