package com.quant.fund.service;

import java.math.BigDecimal;
import java.util.List;

import com.quant.fund.dto.BatchImportProgressVO;
import com.quant.fund.dto.EtfCandidateVO;
import com.quant.fund.dto.FundCheckVO;
import com.quant.fund.dto.TaskProgressVO;

/**
 * 基金数据导入服务（FR3）
 */
public interface ImportService {

    /** 代码校验：识别 ETF/场外指数基金并返回档案 */
    FundCheckVO check(String fundCode);

    /** 发起历史导入（异步），返回进度 taskId */
    String importFund(String fundCode);

    TaskProgressVO progress(String taskId);

    /**
     * 批量导入候选：全市场场内基金（ETF）列表，按条件筛选后返回（V5.41）。
     *
     * @param minScaleYi 规模下限（亿元，总市值口径）
     * @param minYears   上市年限下限（用上市日期近似成立日期）
     * @return 按规模降序的候选清单（含"是否已在池中"标记，结果在 Redis 缓存 10 分钟）
     */
    List<EtfCandidateVO> etfCandidates(BigDecimal minScaleYi, int minYears);

    /**
     * 批量导入候选：全市场**场外**指数基金（东财基金排行「指数型」分类，V6.27），
     * 按成立年限 + 规模筛选后返回（V6.28 起规模为季报口径净资产，快照未就绪时不按规模过滤）。
     *
     * @param minScaleYi 规模下限（亿元，最新季报口径；≤0 表示不限）
     * @param minYears   成立年限下限（真实成立日期）
     * @return 候选清单 + 规模快照状态（按近1年涨幅降序，含"是否已在池中"标记，结果在 Redis 缓存 10 分钟）
     */
    com.quant.fund.dto.OtcCandidatesVO otcCandidates(BigDecimal minScaleYi, int minYears);

    /**
     * 发起批量导入（V5.41）：串行复用单基金管线，与数据同步任务互斥，
     * 数据源封堵时自动暂停续跑；返回 taskId 供进度轮询。
     *
     * @param codes 待导入基金代码（服务端负责清洗/去重/剔除已在池中/上限校验）
     * @return taskId + 接收统计（accepted=实际排队只数，skippedExisting=已在池中跳过的代码）
     */
    BatchStartResult startBatch(List<String> codes);

    /** 批量导入进度轮询 */
    BatchImportProgressVO batchProgress(String taskId);

    /**
     * 自动导入"符合条件且不在自选池"的新基金（V6.30，周三 13:01 定时任务与手动预演同一执行体）：
     * 场内 ETF（规模 ≥ minScaleYi 亿 且 上市 ≥ minYears 年）+ 场外指数基金（最新季报净资产 ≥ minScaleYi 亿
     * 且 成立 ≥ minYears 年），剔除已在池中的；按单批上限 200 只**分批串行**执行（等上一批跑完再提交下一批）。
     *
     * <p>⚠️ dryRun=false 会**真实导入**（写入自选池并拉取全部历史数据，耗时可能很长）——手动触发前先确认（铁律 14）。
     * 场外候选依赖规模快照：快照未就绪时本轮跳过场外部分并记入 note，不按"无规模过滤"静默放行。
     *
     * @param minScaleYi 规模下限（亿元；场内=板块实时口径、场外=最新季报净资产口径）
     * @param minYears   成立/上市年限下限（年）
     * @param dryRun     true=只返回将导入的清单、不真正导入（人工预演用）
     * @return 汇总（场内/场外新增清单、实际接收导入的清单、备注）
     */
    AutoImportReport autoImportEligibleFunds(BigDecimal minScaleYi, int minYears, boolean dryRun);

    /** 自动导入汇总（V6.30）：dryRun=true 时 imported 为空、只有清单；note 记录跳过原因/超时等 */
    record AutoImportReport(boolean dryRun, java.util.List<String> etfNew, java.util.List<String> otcNew,
                            java.util.List<String> imported, String note) {
    }

    /** 批量发起结果（铁律 11：把接收/跳过的真实状态返回给前端核对） */
    record BatchStartResult(String taskId, List<String> accepted, List<String> skippedExisting) {
    }
}
