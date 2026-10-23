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
     * 落一行"每日估算"规模与份额数据（V6.15；V6.55 加份额字段）。
     * 幂等键与披露值一致（基金 + stat_date），同一交易日重复跑覆盖；新增字段传 null 时保留库内已有值。
     *
     * @param statDate    统计日（交易日；净值日）
     * @param scale       净资产规模（亿元）
     * @param totalShare  总份额（份；场内 f84 / 场外披露净资产÷披露日净值估算），可空
     * @param tradeShare  当日成交份额（份；场内成交量×100，场外无数据传 null）
     * @param tradeAmount 当日成交额（元；场内 K 线成交额，场外无数据传 null）
     */
    void recordEstimated(String fundCode, LocalDate statDate, BigDecimal scale,
                         BigDecimal totalShare, BigDecimal tradeShare, BigDecimal tradeAmount);

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
