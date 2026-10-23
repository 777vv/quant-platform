package com.quant.strategy.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.quant.common.auth.PermissionCodes;
import com.quant.common.result.PageResult;
import com.quant.common.result.R;
import com.quant.strategy.dto.MaSignalItemVO;
import com.quant.strategy.service.MaSignalService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 均线信号接口（V5.70）：查询 + 手动触发判定。
 */
@RestController
@RequestMapping("/api/ma-signals")
public class MaSignalController {

    private final MaSignalService maSignalService;

    public MaSignalController(MaSignalService maSignalService) {
        this.maSignalService = maSignalService;
    }

    /**
     * 均线信号分页（新→旧），可按基金代码/关键词/方向过滤。
     * V6.52：新增 tag（标签名）与 fundType（1=场内 ETF / 2=场外指数基金）筛选，口径与基金池列表一致。
     */
    @GetMapping("/page")
    public R<PageResult<MaSignalItemVO>> page(
            @RequestParam(required = false) String fundCode,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) Integer fundType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return R.ok(maSignalService.page(fundCode, keyword, direction, tag, fundType, page, size));
    }

    /** 某基金的全部均线信号（基金详情【信号查询】页签用，不分页） */
    @GetMapping("/by-fund/{fundCode}")
    public R<List<MaSignalItemVO>> listByFund(@PathVariable String fundCode) {
        return R.ok(maSignalService.listByFund(fundCode));
    }

    /** 手动触发一次判定（与 10:00 定时任务同逻辑，幂等；需要手动同步权限） */
    @PostMapping("/compute")
    @SaCheckPermission(PermissionCodes.ACTION_SYNC)
    public R<Integer> compute() {
        return R.ok(maSignalService.computeFromMaDaily());
    }
}
