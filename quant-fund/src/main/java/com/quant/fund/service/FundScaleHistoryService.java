package com.quant.fund.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.quant.fund.dto.FundScaleHistoryVO;

/**
 * 基金规模历史服务：随档案刷新逐日积累规模快照（同日幂等覆盖），
 * 供行情图「基金规模」副图与走势对比使用。
 */
public interface FundScaleHistoryService {

    /**
     * 记录一次规模快照（幂等：同基金同日覆盖）。
     * 只在档案刷新成功后调用；scale 为空（东财未披露）时跳过不落行。
     *
     * @param fundCode  基金代码
     * @param scale     净资产规模（亿元），可为 null
     * @param scaleDate 规模数据截止日，可为 null
     */
    void record(String fundCode, java.math.BigDecimal scale, java.time.LocalDate scaleDate);

    /**
     * 某基金的规模历史（按日期升序）。
     *
     * @param fundCode 基金代码
     * @return 日期 + 规模（亿元）列表
     */
    List<FundScaleHistoryVO> history(String fundCode);

    /**
     * 落一行"每日估算"规模（V6.15，场内 ETF：份额 × 当日单位净值）。
     * 幂等键与披露值一致（基金 + stat_date），同一交易日重复跑覆盖。
     *
     * @param statDate 统计日（交易日；净值日）
     */
    void recordEstimated(String fundCode, LocalDate statDate, BigDecimal scale);

    /**
     * 多只基金的"最新每日估算"（供列表/详情展示，一次查询批量取）。
     *
     * @return 基金代码 → 最新估算（含数据日）；无估算的基金不在 map 中
     */
    java.util.Map<String, EstimatedScale> latestEstimated(java.util.Collection<String> fundCodes);

    /**
     * 最新每日估算（单只）。
     */
    EstimatedScale latestEstimated(String fundCode);

    /** 每日估算规模（亿元）+ 数据日 */
    record EstimatedScale(BigDecimal scale, LocalDate date) {
    }
}
