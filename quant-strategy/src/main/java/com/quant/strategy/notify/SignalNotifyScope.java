package com.quant.strategy.notify;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.quant.fund.entity.TradeFlow;
import com.quant.fund.mapper.TradeFlowMapper;
import com.quant.strategy.entity.StrategyConfig;
import com.quant.strategy.mapper.StrategyConfigMapper;

/**
 * 信号通知范围（V6.26 用户口径）：**只有"曾经/当前持有"且"配置了策略"的基金**产生的买卖信号
 * 才发邮件 / 微信；其它基金的信号照常计算入库（页面上可查、AI 可答），只是**不推送**。
 *
 * <p>口径定义（用户原话："只有持仓（这里的统计口径是曾经持有，但是现在市值为 0 的也算）并且
 * 配置了策略的基金产生信号才需要邮件或微信通知"）：
 * <ul>
 *   <li><b>曾经持有</b> = 该基金有交易流水（买入/卖出/分红任一条即可）——**已清仓、市值为 0 的也算**，
 *       等价于"进过这个账户的基金"；只是收藏在自选池、从未交易过的基金不算。</li>
 *   <li><b>配置了策略</b> = 该基金有**启用中**的策略配置行（`strategy_config.enabled = 1`）。
 *       信号本身就只能由启用的配置算出来，这里仍显式判定一次，是为了规则自洽——
 *       将来若放宽信号生成范围，也不会把"没配策略"的基金误推出去。</li>
 * </ul>
 *
 * <p>两个通道（邮件摘要、企业微信）**共用本组件给出的同一份集合**，避免一处改了另一处漏改。
 */
@Component
public class SignalNotifyScope {

    /** 参与"持有过"判定的流水类型：买入 / 卖出 / 分红（转入转出是账户级流水、不挂基金） */
    private static final List<Integer> FUND_TRADE_TYPES = List.of(1, 2, 3);

    private final TradeFlowMapper tradeFlowMapper;

    private final StrategyConfigMapper configMapper;

    public SignalNotifyScope(TradeFlowMapper tradeFlowMapper, StrategyConfigMapper configMapper) {
        this.tradeFlowMapper = tradeFlowMapper;
        this.configMapper = configMapper;
    }

    /**
     * 需要推送的基金代码集合 = 有启用策略配置 ∩ 有过交易流水。
     *
     * @return 基金代码集合（无满足条件的基金时返回空集合，调用方需按"一条都不推"处理）
     */
    public Set<String> notifiableFundCodes() {
        Set<String> configured = configMapper.selectList(new LambdaQueryWrapper<StrategyConfig>()
                        .eq(StrategyConfig::getEnabled, 1))
                .stream()
                .map(StrategyConfig::getFundCode)
                .collect(Collectors.toSet());
        if (configured.isEmpty()) {
            return Set.of();
        }
        return tradeFlowMapper.selectList(new LambdaQueryWrapper<TradeFlow>()
                        .in(TradeFlow::getFundCode, configured)
                        .in(TradeFlow::getTradeType, FUND_TRADE_TYPES))
                .stream()
                .map(TradeFlow::getFundCode)
                .collect(Collectors.toSet());
    }
}
