package com.quant.strategy.service;

import java.time.LocalDate;
import java.util.List;

import com.quant.common.result.PageResult;
import com.quant.strategy.dto.SignalItemVO;
import com.quant.strategy.entity.SignalRecord;

/**
 * 策略信号服务（FR5，M4-03）：每日为所有启用策略生成买卖建议，幂等入库
 */
public interface SignalService {

    /**
     * 为全部启用策略生成当日信号（单策略失败不影响其余，结果计入 sync_log）。
     *
     * @return 本次生成的信号列表（含 HOLD）
     */
    List<SignalRecord> generateAll();

    /**
     * 近 N 天信号列表（新→旧，仪表盘与详情页共用）。
     */
    List<SignalRecord> recent(int days);

    /**
     * 信号分页查询（【信号查询】页用，新→旧）。
     *
     * @param fundCode     基金代码（精确匹配；空 = 不限）
     * @param direction    方向（BUY/SELL/HOLD；空 = 不限）
     * @param strategyType 策略类型（GRID/VAL_PERCENTILE；空 = 不限）
     * @param startDate    信号日期下界（含；空 = 不限）
     * @param endDate      信号日期上界（含；空 = 不限）
     * @param page         页码（1 起）
     * @param size         每页条数（上限 200，防一次性拉全表）
     * @return 分页结果（已带基金名称与策略展示名）
     */
    PageResult<SignalItemVO> page(String fundCode, String keyword, String direction, String strategyType,
            LocalDate startDate, LocalDate endDate, long page, long size);

    /**
     * 标记信号为已读（仪表盘未读红点消除）。
     *
     * @param ids 信号记录主键列表
     */
    void markRead(List<Long> ids);
}
