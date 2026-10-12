package com.quant.fund.dto;

import java.util.List;

/**
 * 场外指数基金候选响应（V6.28）：包一层以携带**规模快照状态**——规模是季报口径、抓全量快照约需 2 分钟，
 * 首次或缓存过期期间 scaleReady=false（此时列表按成立年限筛选、未按规模过滤，前端提示"规模数据加载中"），
 * 就绪后 scaleReady=true 且 scaleDate 为季报截止日（如 2026-06-30）。快照缓存 7 天、应用启动时预热。
 */
public record OtcCandidatesVO(
        /** 规模快照是否就绪（false=正在后台抓取或抓取失败，本次未按规模过滤） */
        boolean scaleReady,
        /** 规模口径的季报截止日（如 2026-06-30；未就绪为 null） */
        String scaleDate,
        /** 候选清单（按近1年涨幅降序） */
        List<OtcCandidateVO> items) {
}
