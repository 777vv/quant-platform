package com.quant.strategy.dto;

import java.util.List;
import java.util.Map;

import com.quant.strategy.entity.BacktestBatch;
import com.quant.strategy.entity.BacktestRecord;

import lombok.Data;

/**
 * 批量回测批次详情 VO（V5.96）：批次口径 + 该批全部基金的回测记录（不含曲线列，曲线走结果页接口）。
 */
@Data
public class BacktestBatchDetailVO {

    /** 批次本体（含实时进度计数） */
    private BacktestBatch batch;

    /** 本批每只基金的回测记录（按基金代码升序） */
    private List<BacktestRecord> records;

    /** 基金代码 → 基金名称（V6.29）：抽屉的「基金」列与筛选不必等市场信号总览（重接口）即可显示中文名 */
    private Map<String, String> fundNames;
}
