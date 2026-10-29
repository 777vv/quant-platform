package com.quant.fund.service;

import com.quant.fund.dto.MarketSignalVO;

/**
 * 市场信号服务（V5.39）：涨跌榜 / 估值红绿灯 / 技术面与溢价 的指标计算。
 * 纯库内计算、无数据源请求、无定时任务——页面打开即算，数据新鲜度跟随既有同步任务。
 */
public interface MarketSignalService {

    /**
     * 自选池全部基金的市场信号总览。
     *
     * @param peWindow PE 分位窗口：3y=近3年 / 5y=近5年 / 10y=近10年 / all=全历史
     * @return 每基金一行（含标签，前端筛选用）
     */
    MarketSignalVO overview(String peWindow);

    /**
     * 区间涨跌（V6.63 用户要求）：所选日期区间内各基金的实际涨跌幅 / 年化收益 / 最大回撤。
     * 区间内无 K 线数据的基金不返回；数据起始晚于区间起点的，按基金最早有数据那一天起算。
     *
     * @param startDate 起始日（含，null 表示未选区间 → 返回空列表）
     * @param endDate   截止日（含，null 表示未选区间 → 返回空列表）
     * @return 每基金一行（未排序，前端按列排序）
     */
    java.util.List<com.quant.fund.dto.RangePerformanceVO> rangePerformance(java.time.LocalDate startDate,
            java.time.LocalDate endDate);
}
