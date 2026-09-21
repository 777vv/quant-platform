package com.quant.fund.controller;

import com.quant.common.result.R;
import com.quant.fund.dto.FundCheckVO;
import com.quant.fund.dto.TaskProgressVO;
import com.quant.fund.service.ImportService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import lombok.Data;

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
     * 导入请求体
     */
    @Data
    public static class ImportRequest {

        @NotBlank(message = "基金代码不能为空")
        @Size(max = 12, message = "基金代码过长（不超过 12 位）")
        private String code;
    }
}
