package com.quant.strategy.controller;

import java.util.List;

import com.quant.common.result.R;
import com.quant.strategy.entity.SignalRecord;
import com.quant.strategy.service.SignalService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 策略信号接口（FR1/FR5，M4-03）：仪表盘今日信号与历史信号查询、手动触发计算
 */
@RestController
@RequestMapping("/api/strategies")
public class SignalController {

    /** 单次查询最大天数，防止全表扫描式拉取 */
    private static final int MAX_DAYS = 365;

    private final SignalService signalService;

    public SignalController(SignalService signalService) {
        this.signalService = signalService;
    }

    /** 近 N 天信号列表（新→旧；days 缺省 7，上限 365） */
    @GetMapping("/signals")
    public R<List<SignalRecord>> signals(@RequestParam(defaultValue = "7") int days) {
        return R.ok(signalService.recent(Math.min(days, MAX_DAYS)));
    }

    /** 手动触发一轮信号计算（调试/补算用；与定时任务共用 Redisson 锁） */
    @PostMapping("/signals/run")
    public R<Integer> run() {
        return R.ok(signalService.generateAll().size());
    }

    /** 标记信号已读（仪表盘未读红点消除） */
    @PostMapping("/signals/read")
    public R<Void> markRead(@RequestBody SignalReadRequest request) {
        signalService.markRead(request.ids());
        return R.ok();
    }

    /**
     * 标记已读请求体
     */
    public record SignalReadRequest(
            /** 信号记录主键列表 */
            List<Long> ids) {
    }
}
