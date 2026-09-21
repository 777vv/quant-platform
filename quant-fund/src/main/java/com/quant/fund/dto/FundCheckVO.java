package com.quant.fund.dto;

import java.time.LocalDate;

/**
 * 基金代码校验结果。
 *
 * <p>V4.1 起把"池内状态"拆成两个明确事实（原来只有一个 {@code existsInPool}＝"fund_basic 里有这行"，
 * 把「有历史数据」和「在自选池」混为一谈，用户移出自选后再导入会被提示"已存在"，与事实矛盾）：
 * {@link #inPool}（在自选池）/ {@link #removedFromPool}（曾导入、已移出自选）——两者互斥，
 * 都为 false 才是真正的新基金；{@link #lastSyncDate} 顺带告知历史数据到哪一天。
 */
public record FundCheckVO(
        /** 基金代码 */
        String code,
        /** 基金简称 */
        String name,
        /** 1=场内ETF 2=场外指数基金（不支持时为 null） */
        Integer fundType,
        /** 交易所市场 SH/SZ（场外为 null） */
        String market,
        /** 东财基金类型原文（如"指数型-股票"） */
        String fundTypeDesc,
        /** 基金公司 */
        String fundCompany,
        /** 跟踪指数代码（映射表未命中时为 null） */
        String indexCode,
        /** 跟踪指数名称 */
        String indexName,
        /** 成立日期（f10 解析，可能为 null） */
        LocalDate estabDate,
        /** 是否允许导入（指数型基金才允许） */
        boolean supported,
        /** 不支持原因（supported=false 时给前端展示） */
        String reason,
        /** 是否**在自选池**（fund_basic.status=1）：是则本次导入＝重新导入并覆盖刷新历史数据 */
        boolean inPool,
        /** 是否**曾导入、已移出自选**（fund_basic.status=0）：是则本次导入会把它恢复到自选池 */
        boolean removedFromPool,
        /** 本地已有历史数据的截止日（null＝没有历史数据；用于告知本次重新拉取的范围） */
        LocalDate lastSyncDate) {
}
