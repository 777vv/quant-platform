package com.quant.ai.service;

import java.time.LocalDate;
import java.util.List;

import com.quant.ai.dto.AiQuotaRequest;
import com.quant.ai.dto.AiQuotaVO;
import com.quant.ai.dto.AiUsageDayVO;
import com.quant.ai.dto.AiUsageLogVO;
import com.quant.ai.dto.AiUsageRecord;
import com.quant.ai.dto.AiUsageTodayVO;
import com.quant.common.result.PageResult;

/**
 * AI 用量与费用服务（V3.9 起）。
 *
 * <p>口径要点：**一次咨询一行**流水（工具调用的多轮已在调用方累加）、按**自然日**聚合校验额度、
 * 单价随流水**快照**（改价不影响历史）、费用按「输入/缓存命中输入/输出」三段计价；
 * **每日额度是全局一份**（V4.0 起放在 ai_runtime_config，不跟厂商走）。
 */
public interface AiUsageService {

    /**
     * 记录一条用量流水（费用按当前单价快照计算）。
     *
     * <p>实现内部吞掉异常：记账失败只记日志，**绝不影响对话主流程**；
     * 计算出的费用会**回填到 {@code record.cost}**，调用方据此在结束帧里展示"本次 N token ≈ ¥X"。
     *
     * @param record 用量记录（由对话/自检路径填充）
     */
    void record(AiUsageRecord record);

    /** 今日用量概览（含额度、已用比例、是否超限；按自然日聚合） */
    AiUsageTodayVO today();

    /**
     * 近 N 日用量趋势（含今日；没有流水的日期补 0，便于前端直接画图）。
     *
     * @param days 天数（1~{@code 90}，超出按边界收敛）
     */
    List<AiUsageDayVO> summary(int days);

    /**
     * 用量明细分页（按时间倒序）。
     *
     * @param page 页码（从 1 起）
     * @param size 每页条数（上限 100）
     * @param date 指定日期（null = 不限日期）
     */
    PageResult<AiUsageLogVO> logs(long page, long size, LocalDate date);

    /**
     * 额度校验：已超限则抛 {@code BizException}（中文原因含已用/上限/恢复时间）。
     *
     * <p>调用方必须在**发起上游请求之前**调用——超限时连模型都不调，才是真的省钱。
     * 两个维度都没配上限时直接放行（连查询都省掉）。
     */
    void checkQuota();

    /** 每日额度（全局一份：token 上限 / 费用上限 / 预警百分比，界面在【AI用量统计】页） */
    AiQuotaVO quota();

    /**
     * 保存每日额度（全局一份，与厂商无关：额度是"这台机器每天最多花多少"的运营政策）。
     *
     * @param request 额度请求；字段为 null 表示保持原值，0 表示该维度不限制
     */
    void saveQuota(AiQuotaRequest request);
}
