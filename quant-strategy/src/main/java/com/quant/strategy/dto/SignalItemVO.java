package com.quant.strategy.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.quant.strategy.entity.SignalRecord;

/**
 * 信号行视图（【信号查询】页用）：在 signal_record 基础上补齐展示字段——
 * 基金名称（来自 fund_basic，一次批量解析避免逐行查库）与策略展示名（来自策略注册表）。
 *
 * @param id            信号记录主键
 * @param fundCode      基金代码
 * @param fundName      基金名称（库里查不到时为空串，界面回退显示代码）
 * @param strategyType  策略类型码（GRID / VAL_PERCENTILE）
 * @param strategyName  策略展示名（网格交易 / 估值百分位；未知类型回退类型码）
 * @param signalDate    信号日期
 * @param direction     方向（BUY 买入 / SELL 卖出 / HOLD 持有）
 * @param priceAt       信号时的价格/净值
 * @param suggestDesc   建议说明
 * @param readFlag      已读标记（0 未读 / 1 已读）
 * @param notifiedFlag  邮件通知标记（0 未通知 / 1 已通知）
 */
public record SignalItemVO(Long id, String fundCode, String fundName, String strategyType,
        String strategyName, LocalDate signalDate, String direction, BigDecimal priceAt,
        String suggestDesc, Integer readFlag, Integer notifiedFlag) {

    /** 由信号记录 + 解析好的展示字段组装视图 */
    public static SignalItemVO of(SignalRecord record, String fundName, String strategyName) {
        return new SignalItemVO(record.getId(), record.getFundCode(), fundName, record.getStrategyType(),
                strategyName, record.getSignalDate(), record.getDirection(), record.getPriceAt(),
                record.getSuggestDesc(), record.getReadFlag(), record.getNotifiedFlag());
    }
}
