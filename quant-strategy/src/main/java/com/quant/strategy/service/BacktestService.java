package com.quant.strategy.service;

import com.quant.strategy.dto.BacktestRequest;
import com.quant.strategy.entity.BacktestRecord;
import com.quant.strategy.entity.BacktestTradeDetail;
import com.quant.common.result.PageResult;

/**
 * 回测服务
 */
public interface BacktestService {

    /** 发起回测（异步执行），返回回测记录ID */
    Long create(BacktestRequest request);

    BacktestRecord detail(Long id);

    PageResult<BacktestRecord> page(String fundCode, long page, long size);

    PageResult<BacktestTradeDetail> trades(Long backtestId, long page, long size);

    /**
     * 删除回测记录（V5.92 用户要求：避免杂乱的旧回测影响查看）。
     * 同时删除该记录的全部交易明细（backtest_trade_detail 按 backtest_id 关联，无外键需手动清理）。
     *
     * @param id 回测记录 ID
     * @return 是否删除了记录（false = 记录不存在）
     */
    boolean delete(Long id);
}
