package com.quant.fund.dto;

import java.math.BigDecimal;

/**
 * 批量导入候选 ETF（V5.41）：来自东财场内基金板块行情列表，按总市值降序。
 * "规模"为总市值口径（份额×市价 ≈ 净资产规模）；"成立时间"用上市日期近似。
 */
public record EtfCandidateVO(
        /** 基金代码 */
        String fundCode,
        /** 基金名称 */
        String fundName,
        /** 规模（亿元，总市值口径） */
        BigDecimal scaleYi,
        /** 上市日期（近似成立日期） */
        String listedDate,
        /** 是否已在自选池中（前端据此禁选/提示，服务端批量时也会跳过） */
        boolean inPool) {
}
