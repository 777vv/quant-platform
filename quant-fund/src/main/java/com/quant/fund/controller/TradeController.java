package com.quant.fund.controller;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.quant.common.result.PageResult;
import com.quant.common.result.R;
import com.quant.fund.dto.CashBalanceVO;
import com.quant.fund.dto.HoldingVO;
import com.quant.fund.dto.TaskProgressVO;
import com.quant.fund.dto.TradeFlowRequest;
import com.quant.fund.entity.TradeFlow;
import com.quant.fund.service.CashAccountingService;
import com.quant.fund.service.SyncService;
import com.quant.fund.service.TradeService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 交易流水接口（FR2）
 */
@RestController
@RequestMapping("/api")
public class TradeController {

    private final TradeService tradeService;

    /** 现金口径（转出额度校验与提示） */
    private final CashAccountingService cashAccountingService;

    private final SyncService syncService;

    public TradeController(TradeService tradeService, SyncService syncService,
                           CashAccountingService cashAccountingService) {
        this.tradeService = tradeService;
        this.syncService = syncService;
        this.cashAccountingService = cashAccountingService;
    }

    /** 持仓列表（由交易流水汇总：份额/摊薄成本/浮动与已实现盈亏） */
    @GetMapping("/funds/holdings")
    public R<List<HoldingVO>> holdings() {
        return R.ok(tradeService.holdings());
    }

    /**
     * 账户现金口径（转出额度提示用；比资产总览轻，不计算区间收益）。
     */
    @GetMapping("/trades/cash-balance")
    public R<CashBalanceVO> cashBalance() {
        return R.ok(new CashBalanceVO(
                cashAccountingService.cashBalance().setScale(2, RoundingMode.HALF_UP),
                cashAccountingService.transferNetIn().setScale(2, RoundingMode.HALF_UP),
                cashAccountingService.netInvested().setScale(2, RoundingMode.HALF_UP),
                cashAccountingService.transferableLimit().setScale(2, RoundingMode.HALF_UP)));
    }

    /**
     * 交易流水分页查询（全部筛选条件可选）。
     *
     * @param fundCode  基金代码，为空表示不限
     * @param tradeType 交易类型（1 买入 / 2 卖出 / 3 分红 / 4 转入 / 5 转出），为空表示不限
     * @param startDate 交易日期下限（yyyy-MM-dd，含）
     * @param endDate   交易日期上限（yyyy-MM-dd，含）
     * @param page      页码（从 1 开始）
     * @param size      每页条数
     */
    @GetMapping("/trades")
    public R<PageResult<TradeFlow>> page(@RequestParam(required = false) String fundCode,
            @RequestParam(required = false) Integer tradeType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "10") long size) {
        return R.ok(tradeService.page(fundCode, tradeType, startDate, endDate, page, size));
    }

    /** 新增交易流水（自动触发该基金持仓重算） */
    @PostMapping("/trades")
    public R<Void> add(@Valid @RequestBody TradeFlowRequest request) {
        tradeService.add(request);
        return R.ok();
    }

    /** 修改交易流水（新旧基金代码不一致时两只都重算） */
    @PutMapping("/trades/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody TradeFlowRequest request) {
        tradeService.update(id, request);
        return R.ok();
    }

    /** 删除交易流水（自动触发持仓重算） */
    @DeleteMapping("/trades/{id}")
    public R<Void> delete(@PathVariable Long id) {
        tradeService.delete(id);
        return R.ok();
    }

    /** 手动单基金增量同步（异步），返回进度 taskId */
    @PostMapping("/funds/{code}/sync")
    public R<Map<String, String>> sync(@PathVariable String code) {
        String taskId = syncService.manualSync(code);
        return R.ok(Map.of("taskId", taskId));
    }

    /** 手动同步进度轮询 */
    @GetMapping("/sync/progress")
    public R<TaskProgressVO> syncProgress(@RequestParam String taskId) {
        return R.ok(syncService.manualProgress(taskId));
    }
}
