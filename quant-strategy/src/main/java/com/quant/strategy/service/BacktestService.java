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
}
