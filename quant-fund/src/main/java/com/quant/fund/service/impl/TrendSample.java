package com.quant.fund.service.impl;

import java.math.BigDecimal;

/**
 * 指数盘中走势采样点（Redis List 元素的 JSON 结构，行情刷新任务写入、仪表盘读取）
 */
record TrendSample(String time, BigDecimal price) {
}
