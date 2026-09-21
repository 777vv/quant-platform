package com.quant.fund.dto;

import java.util.List;

/**
 * 基金标签视图（标签库条目 + 展示配色索引）
 */
public record FundTagVO(
        /** 标签 ID */
        Long id,
        /** 标签名 */
        String name,
        /** 排序号 */
        Integer sortNo,
        /** 该标签下的基金数量（标签库管理时展示，便于判断删除影响） */
        Long fundCount) {
}
