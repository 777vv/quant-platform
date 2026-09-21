package com.quant.strategy.core;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 统一行情序列：ETF 前复权日K / 场外复权净值 / 指数 PE 合并抽象（技术文档 6.4）
 */
public class MarketDataSeries {

    /**
     * 单日数据点：ETF open=开盘价 close=前复权收盘；场外 open=close=单位净值；pe 为该日跟踪指数估值（可空）
     */
    public record DayPoint(LocalDate date, BigDecimal open, BigDecimal close, BigDecimal pe) {
    }

    private final boolean etf;

    private final LocalDate startDate;

    private final LocalDate endDate;

    private final java.util.List<DayPoint> points;

    public MarketDataSeries(boolean etf, java.util.List<DayPoint> points) {
        this.etf = etf;
        this.points = points;
        this.startDate = points.isEmpty() ? null : points.get(0).date();
        this.endDate = points.isEmpty() ? null : points.get(points.size() - 1).date();
    }

    public boolean isEtf() {
        return etf;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public java.util.List<DayPoint> getPoints() {
        return points;
    }

    public int size() {
        return points.size();
    }

    public DayPoint get(int index) {
        return points.get(index);
    }
}
