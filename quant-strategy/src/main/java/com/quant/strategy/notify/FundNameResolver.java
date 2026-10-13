package com.quant.strategy.notify;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.quant.fund.entity.FundBasic;
import com.quant.fund.mapper.FundBasicMapper;
import com.quant.strategy.entity.SignalRecord;

/**
 * 基金名称批量解析（邮件/微信两条通知通道共用，V6.39 从 WeComNotifyServiceImpl 抽出单一来源）。
 * 一次查库解析全部代码；查不到名称的基金不在结果 Map 中，调用方回退为只显示代码。
 */
final class FundNameResolver {

    private FundNameResolver() {
    }

    /**
     * 按信号清单批量解析基金名称。
     *
     * @param mapper  基金档案 Mapper（quant-fund 模块提供）
     * @param signals 待通知的信号清单（取基金代码去重后一次查询）
     * @return 基金代码 → 基金名称
     */
    static Map<String, String> resolve(FundBasicMapper mapper, List<SignalRecord> signals) {
        List<String> codes = signals.stream().map(SignalRecord::getFundCode).distinct().toList();
        if (codes.isEmpty()) {
            return Map.of();
        }
        Map<String, String> names = new HashMap<>();
        mapper.selectList(new LambdaQueryWrapper<FundBasic>().in(FundBasic::getFundCode, codes))
                .forEach(fund -> names.put(fund.getFundCode(), fund.getFundName()));
        return names;
    }
}
