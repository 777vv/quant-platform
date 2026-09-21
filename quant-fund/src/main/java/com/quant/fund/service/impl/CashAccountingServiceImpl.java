package com.quant.fund.service.impl;

import java.math.BigDecimal;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.quant.fund.entity.FundBasic;
import com.quant.fund.entity.TradeFlow;
import com.quant.fund.enums.TradeTypeEnum;
import com.quant.fund.mapper.FundBasicMapper;
import com.quant.fund.mapper.TradeFlowMapper;
import com.quant.fund.service.CashAccountingService;
import org.springframework.stereotype.Service;

/**
 * 账户资金口径实现：仅依赖流水与基金表，可被交易服务与统计服务共同注入（无循环依赖）。
 */
@Service
public class CashAccountingServiceImpl implements CashAccountingService {

    private final TradeFlowMapper tradeFlowMapper;

    private final FundBasicMapper fundBasicMapper;

    public CashAccountingServiceImpl(TradeFlowMapper tradeFlowMapper, FundBasicMapper fundBasicMapper) {
        this.tradeFlowMapper = tradeFlowMapper;
        this.fundBasicMapper = fundBasicMapper;
    }

    @Override
    public BigDecimal transferNetIn() {
        List<TradeFlow> transfers = tradeFlowMapper.selectList(new LambdaQueryWrapper<TradeFlow>()
                .isNull(TradeFlow::getFundCode)
                .in(TradeFlow::getTradeType, TradeTypeEnum.TRANSFER_IN.getCode(), TradeTypeEnum.TRANSFER_OUT.getCode()));
        BigDecimal net = BigDecimal.ZERO;
        for (TradeFlow flow : transfers) {
            BigDecimal amount = nvl(flow.getAmount());
            net = TradeTypeEnum.of(flow.getTradeType()) == TradeTypeEnum.TRANSFER_IN
                    ? net.add(amount) : net.subtract(amount);
        }
        return net;
    }

    @Override
    public BigDecimal netInvested() {
        BigDecimal netInvested = BigDecimal.ZERO;
        for (FundBasic fund : tradedFunds()) {
            List<TradeFlow> flows = tradeFlowMapper.selectList(new LambdaQueryWrapper<TradeFlow>()
                    .eq(TradeFlow::getFundCode, fund.getFundCode()));
            for (TradeFlow flow : flows) {
                BigDecimal amount = nvl(flow.getAmount());
                BigDecimal fee = nvl(flow.getFee());
                switch (TradeTypeEnum.of(flow.getTradeType())) {
                    case BUY -> netInvested = netInvested.add(amount).add(fee);
                    case SELL, DIVIDEND -> netInvested = netInvested.subtract(amount).add(fee);
                    default -> {
                        // 划转没有基金归属，不会出现在这里的流水集合中
                    }
                }
            }
        }
        return netInvested;
    }

    @Override
    public BigDecimal cashBalance() {
        return transferNetIn().subtract(netInvested());
    }

    /** 有过流水且仍在自选池的基金（净投入的统计范围；口径限制见接口注释） */
    private List<FundBasic> tradedFunds() {
        List<String> tradedCodes = tradeFlowMapper.selectList(new LambdaQueryWrapper<TradeFlow>()
                        .select(TradeFlow::getFundCode)
                        .isNotNull(TradeFlow::getFundCode)
                        .groupBy(TradeFlow::getFundCode))
                .stream().map(TradeFlow::getFundCode).toList();
        if (tradedCodes.isEmpty()) {
            return List.of();
        }
        return fundBasicMapper.selectList(new LambdaQueryWrapper<FundBasic>()
                .eq(FundBasic::getStatus, 1).in(FundBasic::getFundCode, tradedCodes));
    }

    /** 空值归零（金额/费用字段在历史数据中可能为 null） */
    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
