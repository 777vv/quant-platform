package com.quant.strategy.service;

import com.quant.common.result.PageResult;
import com.quant.strategy.dto.BacktestBatchDetailVO;
import com.quant.strategy.dto.BatchBacktestRequest;
import com.quant.strategy.entity.BacktestBatch;

/**
 * 批量回测服务（V5.96）：一批 = 一套策略参数 × N 只基金；批次内 4 线程并发，进度实时落库。
 */
public interface BacktestBatchService {

    /**
     * 发起批量回测（立即返回批次 ID，后台异步执行）。
     *
     * @param request 批量请求（统一策略参数 + 基金列表 + 区间 + 初始资金口径）
     * @return 批次 ID
     */
    Long create(BatchBacktestRequest request);

    /**
     * 批次分页（按发起时间倒序）。
     *
     * @param strategyType 策略类型过滤（可空；前端"回填上一次批量配置"按类型取最近一批用）
     */
    PageResult<BacktestBatch> page(long page, long size, String strategyType);

    /**
     * 批次详情：批次口径 + 全部基金回测记录（不含曲线列）。
     */
    BacktestBatchDetailVO detail(Long id);
}
