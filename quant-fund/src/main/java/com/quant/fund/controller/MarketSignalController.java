package com.quant.fund.controller;

import com.quant.common.result.R;
import com.quant.fund.dto.MarketSignalVO;
import com.quant.common.auth.PermissionCodes;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.quant.fund.dto.FundMaVO;
import com.quant.fund.dto.MaRunResultVO;
import com.quant.fund.service.FundMaService;
import com.quant.fund.service.MarketSignalService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 市场信号接口（V5.39）：自选池全部基金的涨跌榜 / 估值红绿灯 / 技术面与溢价 指标，
 * 供「市场信号」页一次性渲染。纯只读、库内计算，无副作用（不写 signal_record、不触发同步）。
 */
@RestController
@RequestMapping("/api/market-signals")
public class MarketSignalController {

    private final MarketSignalService marketSignalService;

    private final FundMaService fundMaService;

    public MarketSignalController(MarketSignalService marketSignalService, FundMaService fundMaService) {
        this.marketSignalService = marketSignalService;
        this.fundMaService = fundMaService;
    }

    /**
     * 市场信号总览。
     *
     * @param peWindow PE 分位窗口：3y=近3年 / 5y=近5年 / 10y=近10年（默认）/ all=全历史
     */
    @GetMapping("/overview")
    public R<MarketSignalVO> overview(@RequestParam(defaultValue = "10y") String peWindow) {
        return R.ok(marketSignalService.overview(peWindow));
    }

    /**
     * 【均价】页签数据（V5.68）：每只自选基金最新一条均线快照（现价 + 5~250 日均价 + 现价/均线比值）。
     * 数据读 fund_ma_daily 表（每日 23:00 定时任务 / 手动刷新 / 回跑写入），打开页签零计算成本。
     */
    @GetMapping("/ma")
    public R<java.util.List<FundMaVO>> ma() {
        return R.ok(fundMaService.latest());
    }

    /**
     * 手动刷新均线（V5.68）：按各基金最新行情数据日实时计算并落表（幂等）。
     * 仅交易日允许（非交易日抛业务异常）；写全局数据表，需要手动同步权限。
     */
    @PostMapping("/ma/refresh")
    @SaCheckPermission(PermissionCodes.ACTION_SYNC)
    public R<MaRunResultVO> refreshMa() {
        return R.ok(fundMaService.refresh());
    }

    /**
     * 回跑均线数据（V5.68）：取自选池价格日期并集，回退指定数量的交易日逐日计算落表（幂等，覆盖已有日期）。
     * 用于首次建表补历史或数据修复；写全局数据表，需要手动同步权限。
     */
    @PostMapping("/ma/backfill/{days}")
    @SaCheckPermission(PermissionCodes.ACTION_SYNC)
    public R<MaRunResultVO> backfillMa(@PathVariable("days") int days) {
        return R.ok(fundMaService.backfill(days));
    }
}