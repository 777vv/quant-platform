package com.quant.ai.tool;

import java.util.List;

import com.quant.fund.entity.IndexQuote;
import com.quant.fund.service.SyncService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 工具六：全球指数行情（FR6，M5-03）。
 * 数据源与仪表盘指数看板一致（Redis 缓存 + 盘中 5 分钟刷新）。
 */
@Component
public class IndexQuoteTool {

    private final SyncService syncService;

    public IndexQuoteTool(SyncService syncService) {
        this.syncService = syncService;
    }

    /**
     * 查询全部指数行情。
     *
     * @return 按区域分组的指数点位与涨跌幅；行情未就绪时返回提示
     */
    @Tool(name = "getGlobalIndexQuotes", description = "查询全球指数行情看板数据：A股（上证指数、深证成指、创业板指、科创50、沪深300）、港股（恒生指数、恒生科技）、美股（道琼斯、纳斯达克、标普500）、其他（日经225、德国DAX、英国富时100、法国CAC40）的点位与涨跌幅。无需参数。")
    public String getGlobalIndexQuotes() {
        List<IndexQuote> quotes = syncService.getIndexQuotes();
        if (quotes.isEmpty()) {
            return "暂时没有指数行情快照，可能是数据源不可用，请稍后在仪表盘重试。";
        }
        StringBuilder builder = new StringBuilder("全球指数行情（共 ").append(quotes.size()).append(" 个）：\n");
        for (IndexQuote quote : quotes) {
            builder.append("- ").append(quote.getIndexName()).append("（").append(regionName(quote.getRegion()))
                    .append("）").append(quote.getLastPrice())
                    .append("  涨跌额 ").append(quote.getChangeAmt())
                    .append("  涨跌幅 ").append(quote.getChangePct()).append("%")
                    .append(quote.getQuoteTime() == null ? "" : "  行情时间 " + quote.getQuoteTime())
                    .append("\n");
        }
        return builder.toString();
    }

    /**
     * 按名称查询单个指数。
     *
     * @param nameKeyword 指数名称关键字，如"沪深300""恒生"
     * @return 匹配到的指数行情；无匹配时列出可用名称
     */
    @Tool(name = "getIndexQuoteByName", description = "按名称关键字查询单个指数的最新点位与涨跌幅，例如\"沪深300\"\"恒生\"\"标普\"。nameKeyword 为指数名称的任意关键字。")
    public String getIndexQuoteByName(@ToolParam(description = "指数名称关键字，如 沪深300") String nameKeyword) {
        List<IndexQuote> quotes = syncService.getIndexQuotes();
        if (quotes.isEmpty()) {
            return "暂时没有指数行情快照，请稍后重试。";
        }
        String keyword = nameKeyword == null ? "" : nameKeyword.trim();
        List<IndexQuote> matched = quotes.stream()
                .filter(quote -> quote.getIndexName().contains(keyword) || keyword.contains(quote.getIndexName()))
                .toList();
        if (matched.isEmpty()) {
            return "没有找到名称包含\"" + keyword + "\"的指数。当前可查询：" + String.join("、",
                    quotes.stream().map(IndexQuote::getIndexName).toList());
        }
        StringBuilder builder = new StringBuilder();
        for (IndexQuote quote : matched) {
            builder.append(quote.getIndexName()).append("：").append(quote.getLastPrice())
                    .append("，涨跌幅 ").append(quote.getChangePct()).append("%")
                    .append("，涨跌额 ").append(quote.getChangeAmt())
                    .append(quote.getQuoteTime() == null ? "" : "（" + quote.getQuoteTime() + "）")
                    .append("\n");
        }
        return builder.toString();
    }

    /** 区域中文名 */
    private String regionName(String region) {
        if (region == null) {
            return "其他";
        }
        return switch (region) {
            case "CN" -> "A股";
            case "HK" -> "港股";
            case "US" -> "美股";
            case "ASIA" -> "亚太";
            case "EU" -> "欧洲";
            default -> region;
        };
    }
}
