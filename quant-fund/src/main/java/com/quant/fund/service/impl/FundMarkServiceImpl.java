package com.quant.fund.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.quant.fund.dto.FundMarkVO;
import com.quant.fund.entity.FundDividend;
import com.quant.fund.entity.TradeFlow;
import com.quant.fund.enums.TradeTypeEnum;
import com.quant.fund.mapper.TradeFlowMapper;
import com.quant.fund.service.DividendService;
import com.quant.fund.service.FundMarkService;
import org.springframework.stereotype.Service;

/**
 * 行情图交易标记实现。
 */
@Service
public class FundMarkServiceImpl implements FundMarkService {

    /** 标记类型：买入 */
    private static final String KIND_BUY = "BUY";

    /** 标记类型：卖出 */
    private static final String KIND_SELL = "SELL";

    /** 标记类型：分红（除息日） */
    private static final String KIND_DIVIDEND = "DIVIDEND";

    private final TradeFlowMapper tradeFlowMapper;

    private final DividendService dividendService;

    public FundMarkServiceImpl(TradeFlowMapper tradeFlowMapper, DividendService dividendService) {
        this.tradeFlowMapper = tradeFlowMapper;
        this.dividendService = dividendService;
    }

    @Override
    public List<FundMarkVO> list(String fundCode) {
        Map<String, FundMarkVO> marks = new LinkedHashMap<>();
        List<TradeFlow> flows = tradeFlowMapper.selectList(new LambdaQueryWrapper<TradeFlow>()
                .eq(TradeFlow::getFundCode, fundCode)
                .in(TradeFlow::getTradeType, TradeTypeEnum.BUY.getCode(),
                        TradeTypeEnum.SELL.getCode(), TradeTypeEnum.DIVIDEND.getCode())
                .orderByAsc(TradeFlow::getTradeDate));
        for (TradeFlow flow : flows) {
            TradeTypeEnum type = TradeTypeEnum.of(flow.getTradeType());
            String kind = type == TradeTypeEnum.BUY ? KIND_BUY : type == TradeTypeEnum.SELL ? KIND_SELL : KIND_DIVIDEND;
            marks.put(key(flow.getTradeDate(), kind), new FundMarkVO(flow.getTradeDate(), kind, flowText(type, flow)));
        }
        for (FundDividend dividend : dividendService.listByFund(fundCode)) {
            String key = key(dividend.getExDate(), KIND_DIVIDEND);
            FundMarkVO existing = marks.get(key);
            marks.put(key, new FundMarkVO(dividend.getExDate(), KIND_DIVIDEND,
                    dividendText(dividend, existing)));
        }
        return new ArrayList<>(marks.values());
    }

    /** 合并键：同一天同一类型只保留一条（分红与用户流水同日时合并展示） */
    private String key(LocalDate date, String kind) {
        return date + "#" + kind;
    }

    /** 流水文案：买入/卖出展示份额与成交价，分红展示实际金额 */
    private String flowText(TradeTypeEnum type, TradeFlow flow) {
        if (type == TradeTypeEnum.DIVIDEND) {
            return "分红 " + amount(flow.getAmount()) + " 元";
        }
        return (type == TradeTypeEnum.BUY ? "买入 " : "卖出 ") + share(flow.getShare())
                + " 份 @" + amount(flow.getPrice());
    }

    /** 除息日文案：带上每 10 份派现金；若同日还有用户自录的分红流水，一并展示实际金额 */
    private String dividendText(FundDividend dividend, FundMarkVO sameDayFlow) {
        StringBuilder text = new StringBuilder("除息");
        if (dividend.getPer10Amount() != null) {
            text.append(" 每10份派").append(dividend.getPer10Amount().stripTrailingZeros().toPlainString()).append("元");
        }
        if (sameDayFlow != null) {
            text.append("（你记录的分红：").append(sameDayFlow.text()).append("）");
        }
        return text.toString();
    }

    /** 金额展示：保留 2 位小数 */
    private String amount(BigDecimal value) {
        return value == null ? "--" : value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    /** 份额展示：保留 2 位小数 */
    private String share(BigDecimal value) {
        return value == null ? "--" : value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
