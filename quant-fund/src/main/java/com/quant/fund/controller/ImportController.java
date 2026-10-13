package com.quant.fund.controller;

import com.quant.common.result.R;
import com.quant.fund.dto.BatchImportProgressVO;
import com.quant.fund.dto.EtfCandidateVO;
import com.quant.fund.dto.FundCheckVO;
import com.quant.fund.dto.TaskProgressVO;
import com.quant.fund.service.ImportService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import lombok.Data;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.quant.common.auth.PermissionCodes;

/**
 * 数据导入接口（FR3）
 */
@RestController
@RequestMapping("/api/funds")
@Validated
public class ImportController {

    private final ImportService importService;

    public ImportController(ImportService importService) {
        this.importService = importService;
    }

    /**
     * 基金代码校验：识别指数基金类型（ETF/场外）与跟踪指数。
     *
     * <p>V4.6 起不再限定"6 位纯数字"：代码格式由后端判断（查东财确认是否基金、是否指数基金），
     * 前端只做非空与长度上限（12，与 fund_basic.fund_code 列宽一致）。
     */
    @GetMapping("/check")
    public R<FundCheckVO> check(
            @RequestParam @NotBlank(message = "基金代码不能为空")
            @Size(max = 12, message = "基金代码过长（不超过 12 位）") String code) {
        return R.ok(importService.check(code.trim()));
    }

    /** 发起历史导入（异步任务），返回进度 taskId */
    @SaCheckPermission(com.quant.common.auth.PermissionCodes.ACTION_IMPORT_FUND)
    @PostMapping("/import")
    public R<Map<String, String>> importFund(@Validated @RequestBody ImportRequest request) {
        String taskId = importService.importFund(request.getCode());
        return R.ok(Map.of("taskId", taskId));
    }

    /** 导入进度轮询（Redis，1小时过期） */
    @GetMapping("/import/progress")
    public R<TaskProgressVO> progress(@RequestParam String taskId) {
        return R.ok(importService.progress(taskId));
    }

    /**
     * 批量导入候选（V5.41）：全市场场内 ETF 按条件筛选（规模=总市值口径，成立时间=上市日期近似）。
     * V6.27 起支持场外指数基金（fundType=otc：东财基金排行「指数型」，按成立年限 + 近1年涨幅）。
     * V6.28 起场外同样支持规模筛选（**最新季报口径净资产**，见 OtcCandidatesVO：快照未就绪时
     * scaleReady=false、本次不按规模过滤）。结果缓存 10 分钟；数据源封堵时返回友好错误（"稍后重试"）。
     */
    @GetMapping("/import/batch/candidates")
    public R<Object> candidates(
            @RequestParam(defaultValue = "10") BigDecimal minScaleYi,
            @RequestParam(defaultValue = "6") int minYears,
            @RequestParam(defaultValue = "etf") String fundType) {
        if ("otc".equalsIgnoreCase(fundType)) {
            return R.ok(importService.otcCandidates(minScaleYi, minYears));
        }
        return R.ok(importService.etfCandidates(minScaleYi, minYears));
    }

    /**
     * 发起批量导入（V5.41）：串行逐只导入，与数据同步任务互斥，封堵窗口自动暂停续跑。
     * 返回 taskId 与接收/跳过清单（已在池中的直接跳过并回显，铁律 11）。
     */
    @SaCheckPermission(com.quant.common.auth.PermissionCodes.ACTION_IMPORT_FUND)
    @PostMapping("/import/batch")
    public R<ImportService.BatchStartResult> startBatch(@Validated @RequestBody BatchImportRequest request) {
        return R.ok(importService.startBatch(request.getCodes()));
    }

    /** 批量导入进度轮询（Redis，2小时过期） */
    @GetMapping("/import/batch/progress")
    public R<BatchImportProgressVO> batchProgress(@RequestParam String taskId) {
        return R.ok(importService.batchProgress(taskId));
    }

    /**
     * 自动导入预演/执行（V6.30，与周三 13:01 定时任务同一执行体）：规模 ≥ minScaleYi 亿、
     * 成立/上市 ≥ minYears 年（V6.40 起默认 6，与定时任务同口径）、且不在自选池的新基金（场内 + 场外）。
     *
     * <p>⚠️ **dryRun 默认 true（只看清单、不导入）**——本接口传 dryRun=false 会真实发起批量导入
     * （写入自选池并拉取历史数据，可能耗时较长），调用前先确认（铁律 14）。
     */
    @SaCheckPermission(com.quant.common.auth.PermissionCodes.ACTION_IMPORT_FUND)
    @PostMapping("/import/batch/auto")
    public R<ImportService.AutoImportReport> autoImport(
            @RequestParam(defaultValue = "10") BigDecimal minScaleYi,
            @RequestParam(defaultValue = "6") int minYears,
            @RequestParam(defaultValue = "true") boolean dryRun) {
        return R.ok(importService.autoImportEligibleFunds(minScaleYi, minYears, dryRun));
    }

    /**
     * 导入请求体
     */
    @Data
    public static class ImportRequest {

        @NotBlank(message = "基金代码不能为空")
        @Size(max = 12, message = "基金代码过长（不超过 12 位）")
        private String code;
    }

    /**
     * 批量导入请求体：代码清单（前端负责解析粘贴文本；服务端再做清洗/去重/已在池剔除/上限校验）
     */
    @Data
    public static class BatchImportRequest {

        @NotEmpty(message = "基金代码清单不能为空")
        @Size(max = 500, message = "单次最多提交 500 个代码（实际导入上限以服务端 200 只为准）")
        private List<String> codes;
    }
}
