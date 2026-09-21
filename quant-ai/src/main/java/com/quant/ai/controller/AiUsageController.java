package com.quant.ai.controller;

import java.time.LocalDate;
import java.util.List;

import com.quant.ai.dto.AiQuotaRequest;
import com.quant.ai.dto.AiQuotaVO;
import com.quant.ai.dto.AiUsageDayVO;
import com.quant.ai.dto.AiUsageLogVO;
import com.quant.ai.dto.AiUsageTodayVO;
import com.quant.ai.service.AiUsageService;
import com.quant.common.result.PageResult;
import com.quant.common.result.R;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 用量与费用接口（V3.9 起，界面在 AI 浮窗顶部用量条与【AI用量统计】页）。
 *
 * <p>口径：一次咨询一行流水、按自然日聚合、单价随流水快照；额度校验在对话发起前完成
 * （超限时对话接口直接返回中文原因，且不会调用上游）。**每日额度是全局一份**（V4.0），
 * 与厂商无关——所以额度读写也在这里（额度编辑与用量展示同页）。
 */
@RestController
@RequestMapping("/api/ai/usage")
public class AiUsageController {

    private final AiUsageService usageService;

    public AiUsageController(AiUsageService usageService) {
        this.usageService = usageService;
    }

    /** 今日用量概览（token/费用/次数 + 上限 + 已用比例 + 是否超限） */
    @GetMapping("/today")
    public R<AiUsageTodayVO> today() {
        return R.ok(usageService.today());
    }

    /**
     * 近 N 日用量趋势（含今日，缺数据的日期补 0）。
     *
     * @param days 天数（1~90，默认 7）
     */
    @GetMapping("/summary")
    public R<List<AiUsageDayVO>> summary(@RequestParam(defaultValue = "7") int days) {
        return R.ok(usageService.summary(days));
    }

    /**
     * 用量明细分页（按发生时间倒序）。
     *
     * @param page 页码（从 1 起）
     * @param size 每页条数（上限 100）
     * @param date 指定日期（不传 = 不限日期）
     */
    @GetMapping("/logs")
    public R<PageResult<AiUsageLogVO>> logs(@RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return R.ok(usageService.logs(page, size, date));
    }

    /** 每日额度（全局一份：token 上限 / 费用上限 / 预警百分比） */
    @GetMapping("/quota")
    public R<AiQuotaVO> quota() {
        return R.ok(usageService.quota());
    }

    /**
     * 保存每日额度（全局一份，与厂商无关；保存即生效）。
     *
     * @param request 额度请求：字段为 null 表示保持原值，0 表示该维度不限制
     */
    @PutMapping("/quota")
    public R<Void> saveQuota(@RequestBody AiQuotaRequest request) {
        usageService.saveQuota(request);
        return R.ok();
    }
}
