package com.quant.strategy.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 回测记录（结果永久保存）
 */
@Data
@TableName("backtest_record")
public class BacktestRecord {

    /** 状态：0运行中 1成功 2失败 */
    public static final int STATUS_RUNNING = 0;

    public static final int STATUS_SUCCESS = 1;

    public static final int STATUS_FAILED = 2;

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 基金代码 */
    private String fundCode;

    /** 策略类型（GRID/VAL_PERCENTILE） */
    private String strategyType;

    /** 回测参数快照 JSON（复现依据） */
    private String params;

    /** 回测开始日期（含） */
    private LocalDate startDate;

    /** 回测结束日期（含） */
    private LocalDate endDate;

    /** 初始资金（元） */
    private BigDecimal initialCapital;

    /** 期末总资产（现金+市值） */
    private BigDecimal finalAssets;

    /** 总收益率% = (期末-初始)/初始×100 */
    private BigDecimal totalReturnPct;

    /** 年化收益率% = (1+总收益)^(365/天数)-1 */
    private BigDecimal annualizedPct;

    /** 最大回撤%（负数） */
    private BigDecimal maxDrawdownPct;

    /** 回撤峰值日期 */
    private LocalDate ddPeakDate;

    /** 回撤谷底日期 */
    private LocalDate ddTroughDate;

    /** 回撤修复日期（区间结束仍未修复为 null） */
    private LocalDate ddRecoverDate;

    /** 夏普比率（无风险利率可配，默认2%） */
    private BigDecimal sharpe;

    /** 胜率%（盈利平仓/总平仓，无平仓为 null） */
    private BigDecimal winRate;

    /** 交易笔数 */
    private Integer tradeCount;

    /** 平均仓位份额：回测决策期逐日持仓份额的平均值（衡量策略资金利用程度） */
    private java.math.BigDecimal avgPositionShare;

    /** 持有总收益%：买入持有基准（期初全仓买入并持有到期末）的区间总收益率 */
    private java.math.BigDecimal benchTotalReturnPct;

    /** 持有最大回撤%：买入持有基准曲线的最大回撤 */
    private java.math.BigDecimal benchMaxDrawdownPct;

    /** 平均持仓市值：决策期逐日（持仓份额×收盘价）的均值（持仓资产收益率的分母） */
    private java.math.BigDecimal avgPositionValue;

    /** 平均持仓成本：决策期逐日"摊薄成本×份额"的均值（＝剩余持仓的实际投入，持仓资产收益率的分母） */
    private java.math.BigDecimal avgPositionCost;

    /** 持仓资产收益率%：（期末资产−初始资金）÷ 平均持仓成本×100，衡量实际投出资金的决策质量（V5.14 成本口径） */
    private java.math.BigDecimal positionReturnPct;

    /** 0=运行中 1=成功 2=失败 */
    private Integer status;

    /** 失败原因 */
    private String errorMsg;

    /** 资金曲线 JSON [[date,value],...] */
    private String equityCurve;

    /** 回撤曲线 JSON [[date,dd%],...] */
    private String drawdownCurve;

    /** 基准（买入持有）曲线 JSON [[date,value],...] */
    private String benchmarkCurve;

    /** 创建时间（库默认值） */
    private LocalDateTime createdAt;
}
