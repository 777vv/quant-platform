package com.quant.strategy.service.impl;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.quant.common.exception.BizException;
import com.quant.fund.entity.FundBasic;
import com.quant.fund.mapper.FundBasicMapper;
import com.quant.fund.service.FundTagService;

/**
 * 信号列表的「标签 / 基金类型」筛选范围解析（V6.52）：策略信号与均线信号两个分页接口共用，
 * 把筛选条件解析成基金代码集合，再由调用方按 fund_code IN 过滤（信号表只存代码）。
 *
 * <p>口径与基金池列表一致：标签取预定义标签库下的关联基金；类型 1=场内 ETF、2=场外指数基金；
 * 两个条件同时给出时取交集；均不给出时返回 null 表示"不限"。
 */
final class FundScopeResolver {

    private FundScopeResolver() {
    }

    /**
     * 解析筛选范围。
     *
     * @param fundBasicMapper 基金档案 Mapper（按类型取代码）
     * @param fundTagService  标签服务（按标签名取关联基金）
     * @param tag             标签名（空白=不限）
     * @param fundType        基金类型 1/2（null=不限；其他值报业务异常）
     * @return 命中的基金代码集合；null=不限；空集合=无匹配（调用方应直接返回空页）
     */
    static List<String> resolve(FundBasicMapper fundBasicMapper, FundTagService fundTagService,
                                String tag, Integer fundType) {
        if (fundType != null && fundType != 1 && fundType != 2) {
            throw new BizException("基金类型只支持 1（场内 ETF）或 2（场外指数基金）");
        }
        List<String> codes = null;
        if (tag != null && !tag.isBlank()) {
            codes = fundTagService.fundCodesOfTag(tag.trim());
            if (codes.isEmpty()) {
                return List.of();
            }
        }
        if (fundType != null) {
            List<String> typeCodes = fundBasicMapper.selectList(new LambdaQueryWrapper<FundBasic>()
                            .eq(FundBasic::getFundType, fundType))
                    .stream().map(FundBasic::getFundCode).toList();
            if (codes == null) {
                codes = typeCodes;
            } else {
                // 标签结果与类型结果取交集（两处来源都来自 fund_basic 关联表，规模在千级以内）
                List<String> tagged = codes;
                codes = typeCodes.stream().filter(tagged::contains).toList();
            }
            if (codes.isEmpty()) {
                return List.of();
            }
        }
        return codes;
    }
}
