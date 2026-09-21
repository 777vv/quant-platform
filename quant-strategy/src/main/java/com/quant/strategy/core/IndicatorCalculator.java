package com.quant.strategy.core;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * 回测指标计算（公式按技术文档 6.5 固化）：
 * 总收益/年化/最大回撤(峰-谷-修复日)/夏普(无风险利率可配)/胜率
 */
@Component
public class IndicatorCalculator {

    /**
     * 指标汇总
     */
    public record Metrics(BigDecimal totalReturnPct, BigDecimal annualizedPct, BigDecimal maxDrawdownPct,
                          LocalDate ddPeakDate, LocalDate ddTroughDate, LocalDate ddRecoverDate,
                          BigDecimal sharpe, BigDecimal winRate, int tradeCount) {
    }

    public Metrics calculate(List<Object[]> equityCurve, BigDecimal initialCapital, double riskFreeRate,
                             int tradeCount, int sellCount, int winCount) {
        if (equityCurve == null || equityCurve.isEmpty()) {
            return new Metrics(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, null, null,
                    BigDecimal.ZERO, BigDecimal.ZERO, 0);
        }
        LocalDate first = (LocalDate) equityCurve.get(0)[0];
        LocalDate last = (LocalDate) equityCurve.get(equityCurve.size() - 1)[0];
        BigDecimal finalAssets = (BigDecimal) equityCurve.get(equityCurve.size() - 1)[1];
        long days = Math.max(1, ChronoUnit.DAYS.between(first, last));
        double totalReturn = finalAssets.doubleValue() / initialCapital.doubleValue() - 1;
        double annualized = Math.pow(1 + totalReturn, 365.0 / days) - 1;
        double sharpe = sharpe(equityCurve, riskFreeRate);
        DrawdownStats drawdown = drawdownStats(equityCurve);
        BigDecimal winRate = sellCount == 0 ? null
                : BigDecimal.valueOf(winCount * 100.0 / sellCount).setScale(2, RoundingMode.HALF_UP);
        return new Metrics(
                BigDecimal.valueOf(totalReturn * 100).setScale(4, RoundingMode.HALF_UP),
                BigDecimal.valueOf(annualized * 100).setScale(4, RoundingMode.HALF_UP),
                drawdown.maxDrawdownPct(), drawdown.peakDate(), drawdown.troughDate(), drawdown.recoverDate(),
                BigDecimal.valueOf(sharpe).setScale(4, RoundingMode.HALF_UP),
                winRate, tradeCount);
    }

    private double sharpe(List<Object[]> equityCurve, double riskFreeRate) {
        if (equityCurve.size() < 3) {
            return 0;
        }
        double[] returns = new double[equityCurve.size() - 1];
        for (int i = 1; i < equityCurve.size(); i++) {
            double prev = ((BigDecimal) equityCurve.get(i - 1)[1]).doubleValue();
            double curr = ((BigDecimal) equityCurve.get(i)[1]).doubleValue();
            returns[i - 1] = prev == 0 ? 0 : curr / prev - 1;
        }
        double mean = 0;
        for (double r : returns) {
            mean += r;
        }
        mean /= returns.length;
        double variance = 0;
        for (double r : returns) {
            variance += (r - mean) * (r - mean);
        }
        double std = Math.sqrt(variance / Math.max(1, returns.length - 1));
        double dailyRf = riskFreeRate / 252;
        return std == 0 ? 0 : (mean - dailyRf) / std * Math.sqrt(252);
    }

    private record DrawdownStats(BigDecimal maxDrawdownPct, LocalDate peakDate, LocalDate troughDate,
                                 LocalDate recoverDate) {
    }

    /** 最大回撤：峰、谷、修复日（修复日=回撤后首次回到峰值） */
    private DrawdownStats drawdownStats(List<Object[]> equityCurve) {
        BigDecimal peak = BigDecimal.ZERO;
        BigDecimal maxPeakValue = BigDecimal.ZERO;
        LocalDate peakDate = null;
        LocalDate maxPeakDate = null;
        LocalDate maxTroughDate = null;
        BigDecimal maxDd = BigDecimal.ZERO;
        LocalDate recoverDate = null;
        boolean inDrawdown = false;
        for (Object[] point : equityCurve) {
            LocalDate date = (LocalDate) point[0];
            BigDecimal value = (BigDecimal) point[1];
            if (value.compareTo(peak) >= 0) {
                if (inDrawdown && maxDd.compareTo(BigDecimal.ZERO) < 0 && recoverDate == null) {
                    recoverDate = date;
                }
                peak = value;
                peakDate = date;
                inDrawdown = false;
            } else {
                inDrawdown = true;
                BigDecimal dd = value.divide(peak, 8, RoundingMode.HALF_UP)
                        .subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(100));
                if (dd.compareTo(maxDd) < 0) {
                    maxDd = dd;
                    maxPeakDate = peakDate;
                    maxTroughDate = date;
                    recoverDate = null;
                }
            }
            maxPeakValue = maxPeakValue.max(value);
        }
        return new DrawdownStats(maxDd.setScale(4, RoundingMode.HALF_UP), maxPeakDate, maxTroughDate, recoverDate);
    }
}
