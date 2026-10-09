package com.quant.fund.controller;

import com.quant.common.result.R;
import com.quant.fund.dto.AssetSummaryVO;
import com.quant.fund.dto.DashboardOverviewVO;
import com.quant.fund.dto.ProfitCurveVO;
import com.quant.fund.service.DashboardService;
import com.quant.fund.service.ProfitStatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 仪表盘接口（FR1，M4-01/02/08）：资产总览 / 收益曲线 / 速览区
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final ProfitStatsService profitStatsService;

    private final DashboardService dashboardService;

    public DashboardController(ProfitStatsService profitStatsService, DashboardService dashboardService) {
        this.profitStatsService = profitStatsService;
        this.dashboardService = dashboardService;
    }

    /** 资产总览卡片数据（总市值/当日/浮动/已实现/累计收益率/近7日） */
    @GetMapping("/assets")
    public R<AssetSummaryVO> assets() {
        return R.ok(profitStatsService.summary());
    }

    /** 收益曲线：range=1M/3M/6M/1Y/3Y/ALL，含沪深300 基准与月度汇总 */
    @GetMapping("/profit/curve")
    public R<ProfitCurveVO> profitCurve(@RequestParam(defaultValue = "1Y") String range) {
        return R.ok(profitStatsService.curve(range));
    }

    /** 速览区：持仓概览/自选7日涨跌/配置占比/同步状态 */
    /**
     * 收益日历（V6.07）：某年的逐日收益 + 自然月汇总 + 全年汇总。
     * 前端「点总资产卡片」时才调用（不在仪表盘首屏预取）；服务端按年缓存 5 分钟。
     */
    @GetMapping("/profit/calendar")
    public R<com.quant.fund.dto.ProfitCalendarVO> profitCalendar(@RequestParam int year) {
        return R.ok(profitStatsService.calendar(year));
    }

    @GetMapping("/overview")
    public R<DashboardOverviewVO> overview() {
        return R.ok(dashboardService.overview());
    }
}
