package com.quant.fund.dto;

import java.time.LocalDate;

/**
 * 行情图上的交易标记（买入【b】/卖出【s】/分红【q】）。
 *
 * @param date 标记日期（与 K 线交易日对齐）
 * @param kind 标记类型：BUY 买入 / SELL 卖出 / DIVIDEND 分红（基金除息日）
 * @param text 悬浮提示文案（如"买入 1000 份 @1.5570"、"除息 每10份派0.1500元"）
 */
public record FundMarkVO(
        /** 标记日期 */
        LocalDate date,
        /** 标记类型 */
        String kind,
        /** 悬浮提示文案 */
        String text) {
}
