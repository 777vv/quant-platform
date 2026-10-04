package com.quant.fund.controller;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.quant.common.result.R;
import com.quant.fund.entity.SyncLog;
import com.quant.fund.mapper.SyncLogMapper;
import com.quant.fund.service.SyncService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.quant.common.auth.PermissionCodes;

/**
 * 手动触发同步与同步状态接口（FR2/FR5）
 */
@RestController
@RequestMapping("/api/sync")
public class SyncController {

    private final SyncService syncService;

    private final SyncLogMapper syncLogMapper;

    public SyncController(SyncService syncService, SyncLogMapper syncLogMapper) {
        this.syncService = syncService;
        this.syncLogMapper = syncLogMapper;
    }

    /** 手动触发全局任务：etf / nav / valuation / indices / profiles（档案规模强制刷新） */
    @SaCheckPermission(com.quant.common.auth.PermissionCodes.ACTION_SYNC)
    @PostMapping("/{type}")
    public R<Void> trigger(@PathVariable String type) {
        switch (type) {
            case "etf" -> syncService.syncAllEtfDaily();
            case "nav" -> syncService.syncAllOtcNav();
            case "valuation" -> syncService.syncValuation();
            case "indices" -> syncService.refreshIndexQuotes();
            case "profiles" -> syncService.refreshAllProfiles();
            case "held" -> syncService.syncHeldFundsIntraday();
            case "others" -> syncService.syncNonHeldFundsIntraday();
            default -> throw new com.quant.common.exception.BizException("未知同步类型: " + type);
        }
        return R.ok();
    }

    /** 最近 30 条同步日志（同步状态面板数据源） */
    @GetMapping("/status")
    public R<List<SyncLog>> status() {
        return R.ok(syncLogMapper.selectList(new LambdaQueryWrapper<SyncLog>()
                .orderByDesc(SyncLog::getId)
                .last("limit 30")));
    }
}
