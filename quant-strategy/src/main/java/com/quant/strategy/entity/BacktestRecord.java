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
