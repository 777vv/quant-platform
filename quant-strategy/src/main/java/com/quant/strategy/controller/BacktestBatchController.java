package com.quant.strategy.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.quant.common.auth.PermissionCodes;
import com.quant.common.result.PageResult;
import com.quant.common.result.R;
import com.quant.strategy.dto.BacktestBatchDetailVO;
import com.quant.strategy.dto.BatchBacktestRequest;
import com.quant.strategy.entity.BacktestBatch;
import com.quant.strategy.service.BacktestBatchService;

import cn.dev33.satoken.annotation.SaCheckPermission;

/**
 * 批量回测接口（V5.96）：批次列表 / 批次详情（读）与发起批次（写，须 action:batchBacktest）。
 */
@RestController
@RequestMapping("/api/backtest-batch")
public class BacktestBatchController {

    private final BacktestBatchService batchService;

    public BacktestBatchController(BacktestBatchService batchService) {
        this.batchService = batchService;
    }

    /**
     * 发起批量回测（一套策略参数 × N 只基金；异步执行，返回批次 ID）。
     */
    @PostMapping
    @SaCheckPermission(PermissionCodes.ACTION_BATCH_BACKTEST)
    public R<Long> create(@RequestBody BatchBacktestRequest request) {
        return R.ok(batchService.create(request));
    }

    /**
     * 批次分页（按发起时间倒序）。运行中的批次 success/fail 计数实时变化，前端轮询本接口刷新进度。
     */
    @GetMapping
    public R<PageResult<BacktestBatch>> page(@RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String strategyType) {
        return R.ok(batchService.page(page, size, strategyType));
    }

    /**
     * 批次详情：批次口径 + 该批全部基金回测记录（不含曲线列，看曲线走 /api/backtest/{id} 结果页）。
     */
    @GetMapping("/{id}")
    public R<BacktestBatchDetailVO> detail(@PathVariable Long id) {
        return R.ok(batchService.detail(id));
    }
}
