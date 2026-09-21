package com.quant.strategy.service;

import java.util.List;

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
     * 标记信号为已读（仪表盘未读红点消除）。
     *
     * @param ids 信号记录主键列表
     */
    void markRead(List<Long> ids);
}
