package com.quant.fund.dto;

import java.util.List;

/**
 * 指数看板响应（FR1，M4-06）。
 * degraded 用于向界面如实说明数据新鲜度：外部行情/迷你线拉取失败时置 true，
 * 前端展示"最近一次成功快照"的提示，避免把降级数据误当实时数据。
 */
public record IndexBoardVO(
        /** 指数行情列表（含近 30 交易日迷你线） */
        List<IndexQuoteVO> quotes,
        /** 行情是否处于降级状态（true=来自库内快照而非本次实时拉取） */
        boolean degraded,
        /** 是否存在迷你线缺失（部分指数走势采样拉取失败） */
        boolean trendMissing) {
}
