package com.quant.fund.dto;

import java.util.List;

/**
 * 单只基金的标签集合（详情页展示与编辑用）
 */
public record FundTagsVO(
        /** 基金代码 */
        String fundCode,
        /** 已贴标签（按 sortNo 升序） */
        List<FundTagVO> tags) {
}
