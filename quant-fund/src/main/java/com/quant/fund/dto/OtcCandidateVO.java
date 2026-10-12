package com.quant.fund.dto;

import java.math.BigDecimal;

/**
 * 批量导入候选·场外指数基金（V6.27；V6.28 增加规模）：来自东财基金排行「指数型」分类（rankhandler，ft=zs）。
 * 与场内候选（EtfCandidateVO）的差异：规模来自**最新已披露季报**的期末净资产（亿元，口径与 "F10 净资产规模"
 * 一致；场内是板块实时市值口径），快照未就绪时为 null；"成立时间"就是真实成立日期（非上市日期近似）。
 */
public record OtcCandidateVO(
        /** 基金代码 */
        String fundCode,
        /** 基金名称 */
        String fundName,
        /** 成立日期（真实成立日，非近似） */
        String estabDate,
        /** 近1年涨幅（%，榜单口径；无数据为 null） */
        Double pct1y,
        /** 规模（亿元，最新季报口径、四舍五入保留 2 位；无数据或规模快照未就绪为 null） */
        BigDecimal scaleYi,
        /** 是否已在自选池中（前端据此禁选/提示，服务端批量时也会跳过） */
        boolean inPool) {
}
