package com.quant.ai.tool;

import java.util.List;

import com.quant.fund.entity.FundBasic;
import com.quant.fund.mapper.FundBasicMapper;
import com.quant.strategy.core.StrategyRegistry;
import com.quant.strategy.entity.SignalRecord;
import com.quant.strategy.service.SignalService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 工具五：策略信号（FR6，M5-03）。
 * 返回每日 21:00 任务生成的买卖建议，与仪表盘"最新信号"同源。
 */
@Component
public class SignalTool {

    /** 默认回看天数 */
    private static final int DEFAULT_DAYS = 7;

    /** 单次返回信号条数上限 */
    private static final int MAX_ROWS = 20;

    private final SignalService signalService;

    private final StrategyRegistry strategyRegistry;

    private final FundBasicMapper fundBasicMapper;

    public SignalTool(SignalService signalService, StrategyRegistry strategyRegistry,
                      FundBasicMapper fundBasicMapper) {
        this.signalService = signalService;
        this.strategyRegistry = strategyRegistry;
        this.fundBasicMapper = fundBasicMapper;
    }

    /**
     * 查询近期策略信号。
     *
     * @param days 回看天数（0 或负数按默认 7 天处理）
     * @return 逐行列出信号日、基金、策略、方向与建议；无信号时说明原因
     */
    @Tool(name = "getRecentSignals", description = "查询最近若干天的策略买卖信号（网格交易/估值百分位策略在每日收盘后生成）。用户问\"有什么建议/要不要买/要不要卖\"时，先调用本工具看有没有触发信号。days 为回看天数，如 7、30。")
    public String getRecentSignals(@ToolParam(description = "回看天数，如 7 或 30") int days) {
        int range = days <= 0 ? DEFAULT_DAYS : days;
        List<SignalRecord> signals = signalService.recent(range);
        if (signals.isEmpty()) {
            return "最近 " + range + " 天没有生成策略信号（信号在交易日 21:00 自动计算；若尚未配置策略或全部为持有状态，则不会有买卖信号）。";
        }
        StringBuilder builder = new StringBuilder("最近 ").append(range).append(" 天共 ")
                .append(signals.size()).append(" 条信号：\n");
        int rows = 0;
        for (SignalRecord signal : signals) {
            if (rows++ >= MAX_ROWS) {
                builder.append("（其余信号已省略）\n");
                break;
            }
            builder.append("- ").append(signal.getSignalDate())
                    .append("  ").append(fundName(signal.getFundCode()))
                    .append("（").append(signal.getFundCode()).append("）")
                    .append("  ").append(strategyName(signal.getStrategyType()))
                    .append("  方向：").append(directionName(signal.getDirection()))
                    .append("  价格：").append(signal.getPriceAt() == null ? "-" : signal.getPriceAt())
                    .append("  说明：").append(signal.getSuggestDesc())
                    .append("\n");
        }
        return builder.toString();
    }

    /** 方向中文名 */
    private String directionName(String direction) {
        return switch (direction == null ? "" : direction) {
            case "BUY" -> "买入";
            case "SELL" -> "卖出";
            default -> "持有";
        };
    }

    /** 策略中文名（未知类型回退类型码） */
    private String strategyName(String strategyType) {
        try {
            return strategyRegistry.getRequired(strategyType).name();
        } catch (Exception e) {
            return strategyType;
        }
    }

    /** 基金名称（未在池中时回退代码） */
    private String fundName(String fundCode) {
        FundBasic fund = fundBasicMapper.selectOne(new LambdaQueryWrapper<FundBasic>()
                .eq(FundBasic::getFundCode, fundCode));
        return fund == null ? fundCode : fund.getFundName();
    }
}
