package com.quant.fund.dto;

import lombok.Data;

/**
 * 基金下拉选项（V6.01）：只含代码与名称——前端筛选下拉、名称映射这类"只要代码+名称"的场景专用，
 * 替代重量级的自选列表（watchlist 带最新价/估值/TTM 股息率等 15+ 字段，下拉场景用它是浪费）。
 */
@Data
public class FundOptionVO {

    /** 基金代码 */
    private String fundCode;

    /** 基金名称 */
    private String fundName;

    /** 基金类型：1=场内ETF 2=场外指数基金（对比页等场景决定取 K线还是净值口径） */
    private Integer fundType;
}
