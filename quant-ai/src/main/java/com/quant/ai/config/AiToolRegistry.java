package com.quant.ai.config;

import java.util.List;

import com.quant.ai.tool.FundHistoryTool;
import com.quant.ai.tool.FundInfoTool;
import com.quant.ai.tool.FundQuoteTool;
import com.quant.ai.tool.IndexQuoteTool;
import com.quant.ai.tool.ManualTool;
import com.quant.ai.tool.PositionTool;
import com.quant.ai.tool.SignalTool;
import org.springframework.stereotype.Component;

/**
 * AI 工具注册表（M5-03）：集中持有全部工具 Bean（V4.2 起七个），供 ChatClient 装配时统一注入。
 * 显式构造注入而非反射收集，新增工具时编译器会提醒补登记，避免"注册了但没生效"。
 */
@Component
public class AiToolRegistry {

    private final FundQuoteTool fundQuoteTool;

    private final FundInfoTool fundInfoTool;

    private final FundHistoryTool fundHistoryTool;

    private final PositionTool positionTool;

    private final SignalTool signalTool;

    private final IndexQuoteTool indexQuoteTool;

    /** 工具七（V4.2）：平台使用手册（回答"这个功能怎么用/参数什么意思"） */
    private final ManualTool manualTool;

    public AiToolRegistry(FundQuoteTool fundQuoteTool, FundInfoTool fundInfoTool, FundHistoryTool fundHistoryTool,
                          PositionTool positionTool, SignalTool signalTool, IndexQuoteTool indexQuoteTool,
                          ManualTool manualTool) {
        this.fundQuoteTool = fundQuoteTool;
        this.fundInfoTool = fundInfoTool;
        this.fundHistoryTool = fundHistoryTool;
        this.positionTool = positionTool;
        this.signalTool = signalTool;
        this.indexQuoteTool = indexQuoteTool;
        this.manualTool = manualTool;
    }

    /**
     * 返回全部工具实例（ChatClient.defaultTools 的可变参数入参）。
     */
    public Object[] toolArray() {
        return List.of(fundQuoteTool, fundInfoTool, fundHistoryTool, positionTool, signalTool, indexQuoteTool,
                manualTool).toArray();
    }
}
