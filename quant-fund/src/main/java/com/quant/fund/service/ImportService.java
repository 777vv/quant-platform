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
     * 发起批量导入（V5.41）：串行复用单基金管线，与数据同步任务互斥，
     * 数据源封堵时自动暂停续跑；返回 taskId 供进度轮询。
     *
     * @param codes 待导入基金代码（服务端负责清洗/去重/剔除已在池中/上限校验）
     * @return taskId + 接收统计（accepted=实际排队只数，skippedExisting=已在池中跳过的代码）
     */
    BatchStartResult startBatch(List<String> codes);

    /** 批量导入进度轮询 */
    BatchImportProgressVO batchProgress(String taskId);

    /** 批量发起结果（铁律 11：把接收/跳过的真实状态返回给前端核对） */
    record BatchStartResult(String taskId, List<String> accepted, List<String> skippedExisting) {
    }
}
