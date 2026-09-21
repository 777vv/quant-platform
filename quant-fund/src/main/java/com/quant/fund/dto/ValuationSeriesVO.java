package com.quant.fund.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 基金估值序列（经跟踪指数关联，技术文档 3.4 决策 #2：估值按指数维度唯一存储）
 */
public record ValuationSeriesVO(
        /** 跟踪指数代码（未识别为 null） */
        String indexCode,
        /** 跟踪指数名称 */
        String indexName,
        /** 估值指标（当前仅 PE） */
        String metric,
        /** 区间内 PE 序列（升序） */
        List<SeriesPoint> series,
        /** 最新 PE */
        BigDecimal latestPe,
        /** 近10年 PE 百分位（0-100，保留1位） */
        BigDecimal currentPercentile,
        /** 是否有数据（深市指数等无来源时 false，前端降级提示） */
        boolean hasData) {
}
