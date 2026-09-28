package com.quant.strategy.controller;

import com.quant.common.result.R;
import com.quant.fund.dto.AllocationCheckVO;
import com.quant.fund.dto.AllocationConfigRequest;
import com.quant.fund.entity.AllocationConfig;
import com.quant.fund.service.AllocationService;
import com.quant.strategy.task.AllocationJob;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 全局仓位配置接口（V5.36，平台配置 → 仓位配置卡）：
 * 查看/保存五类资产的目标占比范围，以及手动触发一次检查（与每周二任务同一执行体）。
 */
@RestController
@RequestMapping("/api/allocation")
public class AllocationController {

    private final AllocationService allocationService;

    private final AllocationJob allocationJob;

    public AllocationController(AllocationService allocationService, AllocationJob allocationJob) {
        this.allocationService = allocationService;
        this.allocationJob = allocationJob;
    }

    /** 当前仓位配置（单行；缺行时返回默认值） */
    @GetMapping("/config")
    public R<AllocationConfig> config() {
        return R.ok(allocationService.config());
    }

    /** 保存仓位配置（min ≤ max 由校验保证；返回保存后的配置便于前端核对） */
    @PutMapping("/config")
    public R<AllocationConfig> save(@Valid @RequestBody AllocationConfigRequest request) {
        allocationService.save(request);
        return R.ok(allocationService.config());
    }

    /**
     * 手动触发一次检查（与每周二任务同一执行体）。
     * 返回检查明细与是否发送了告警，便于"立即检查"按钮给出可读反馈。
     */
    @PostMapping("/check")
    public R<Map<String, Object>> check() {
        AllocationCheckVO result = allocationService.check();
        // 注意：runner 里会重复算一次 check（成本是一次本地查询，可接受），
        // 但告警发送必须以 runner 的结果为准，避免双发
        boolean alerted = allocationJob.runOnce("manual");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("alerted", alerted);
        body.put("result", result);
        return R.ok(body);
    }
}
