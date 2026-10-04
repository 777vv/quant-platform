package com.quant.strategy.controller;

import java.util.Map;

import com.quant.common.result.PageResult;
import com.quant.common.result.R;
import com.quant.strategy.dto.BacktestRequest;
import com.quant.strategy.entity.BacktestRecord;
import com.quant.strategy.entity.BacktestTradeDetail;
import com.quant.strategy.service.BacktestService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.quant.common.auth.PermissionCodes;

/**
 * 回测接口（FR2）
 */
@RestController
@RequestMapping("/api/backtest")
public class BacktestController {

    private final BacktestService backtestService;

    public BacktestController(BacktestService backtestService) {
        this.backtestService = backtestService;
    }

    /** 发起回测（异步执行），返回回测记录 ID */
    @SaCheckPermission(com.quant.common.auth.PermissionCodes.ACTION_STRATEGY)
    @PostMapping
    public R<Map<String, Long>> create(@RequestBody BacktestRequest request) {
        return R.ok(Map.of("id", backtestService.create(request)));
    }

    /** 回测记录分页（不含曲线大字段） */
    @GetMapping
    public R<PageResult<BacktestRecord>> page(@RequestParam(required = false) String fundCode,
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "10") long size) {
        return R.ok(backtestService.page(fundCode, page, size));
    }

    /** 回测详情（含资金/回撤/基准三条曲线 JSON） */
    @GetMapping("/{id}")
    public R<BacktestRecord> detail(@PathVariable Long id) {
        return R.ok(backtestService.detail(id));
    }

    /** 回测交易明细分页 */
    @GetMapping("/{id}/trades")
    public R<PageResult<BacktestTradeDetail>> trades(@PathVariable Long id,
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "50") long size) {
        return R.ok(backtestService.trades(id, page, size));
    }
}
