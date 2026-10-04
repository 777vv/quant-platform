package com.quant.fund.controller;

import java.util.List;

import com.quant.common.result.R;
import com.quant.fund.dto.IndexBoardVO;
import com.quant.fund.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.quant.common.auth.PermissionCodes;

/**
 * 全球指数行情接口（FR1 看板数据源，M4-06）
 */
@RestController
@RequestMapping("/api/dashboard")
public class IndexController {

    private final DashboardService dashboardService;

    public IndexController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /** 全球指数行情 + 近 30 交易日迷你线（只读库内快照；degraded=true 表示当前为降级快照） */
    @GetMapping("/indices")
    public R<IndexBoardVO> indices() {
        return R.ok(dashboardService.indexBoard());
    }

    /**
     * 强制刷新全球指数（页面"刷新"按钮）：立即拉取东财最新行情与迷你线并返回，
     * 与定时任务同路径（含锁与降级标记）。
     */
    @SaCheckPermission(com.quant.common.auth.PermissionCodes.ACTION_SYNC)
    @PostMapping("/indices/refresh")
    public R<IndexBoardVO> refreshIndices() {
        dashboardService.forceRefreshIndices();
        return R.ok(dashboardService.indexBoard());
    }
}
