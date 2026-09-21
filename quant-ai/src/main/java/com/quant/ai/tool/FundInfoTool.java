package com.quant.ai.tool;

import com.quant.common.exception.BizException;
import com.quant.fund.dto.FundDetailVO;
import com.quant.fund.enums.FundTypeEnum;
import com.quant.fund.service.FundQueryService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 工具二：基金资料（FR6，M5-03）。
 * 返回类型、跟踪标的、成立日期、基金公司等档案信息，用于回答"这只基金跟踪什么指数"一类问题。
 */
@Component
public class FundInfoTool {

    private final FundQueryService fundQueryService;

    public FundInfoTool(FundQueryService fundQueryService) {
        this.fundQueryService = fundQueryService;
    }

    /**
     * 查询基金档案。
     *
     * @param fundCode 基金代码，如 110003
     * @return 人类可读的档案文本；基金不在自选池时返回提示
     */
    @Tool(name = "getFundInfo", description = "查询某只指数基金的档案：类型（场内ETF/场外指数基金）、**跟踪的指数名称与代码**、成立日期、基金公司、最新价。"
            + "当用户问\"某基金跟踪什么指数/跟踪标的/是什么类型的基金/哪家公司/什么时候成立\"时，必须先调用本工具取数，不要凭记忆回答指数名称。参数为 6 位基金代码。")
    public String getFundInfo(@ToolParam(description = "基金代码，如 110003") String fundCode) {
        try {
            FundDetailVO detail = fundQueryService.detail(fundCode);
            StringBuilder builder = new StringBuilder();
            builder.append(detail.fundName()).append("（").append(detail.fundCode()).append("）\n")
                    .append("- 类型：").append(detail.fundTypeDesc()).append("\n");
            if (FundTypeEnum.ETF.getCode() == detail.fundType()) {
                builder.append("- 交易市场：").append(detail.market() == null ? "-" : detail.market()).append("\n");
            }
            builder.append("- 跟踪指数：").append(detail.indexName() == null ? "未识别（平台估值与百分位依赖跟踪指数，建议手动确认）"
                            : detail.indexName() + "（代码 " + detail.indexCode() + "）").append("\n")
                    .append("- 成立日期：").append(detail.inceptionDate() == null ? "-" : detail.inceptionDate()).append("\n")
                    .append("- 基金公司：").append(detail.fundCompany() == null ? "-" : detail.fundCompany()).append("\n")
                    .append("- 最新价：").append(detail.lastPrice() == null ? "-" : detail.lastPrice())
                    .append("（").append(detail.priceDate() == null ? "-" : detail.priceDate()).append("）");
            return builder.toString();
        } catch (BizException e) {
            return "查询失败：" + e.getMessage() + "（该基金可能未加入自选池）";
        }
    }
}
