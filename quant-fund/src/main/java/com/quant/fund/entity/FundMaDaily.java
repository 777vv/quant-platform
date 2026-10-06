package com.quant.fund.entity;

import java.time.LocalDate;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 基金日均线快照（V5.68）：每交易日收盘后由定时任务/手动刷新/回跑写入，
 * 一只基金一个交易日一条；幂等键 = fund_code + trade_date（重复跑覆盖当天）。
 */
@TableName("fund_ma_daily")
public class FundMaDaily {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 基金代码 */
    private String fundCode;

    /** 行情数据截至日（盘中手动刷新会落到最新数据日，避免日期与价格错位） */
    private LocalDate tradeDate;

    /** 该日最新价（ETF=前复权收盘 / 场外=复权净值回退单位净值） */
    private java.math.BigDecimal closePrice;

    /** 5 个交易日均价（样本不足为 null） */
    private java.math.BigDecimal ma5;

    /** 10 个交易日均价 */
    private java.math.BigDecimal ma10;

    /** 20 个交易日均价 */
    private java.math.BigDecimal ma20;

    /** 30 个交易日均价 */
    private java.math.BigDecimal ma30;

    /** 60 个交易日均价 */
    private java.math.BigDecimal ma60;

    /** 90 个交易日均价 */
    private java.math.BigDecimal ma90;

    /** 120 个交易日均价 */
    private java.math.BigDecimal ma120;

    /** 250 个交易日均价 */
    private java.math.BigDecimal ma250;

    /** 创建时间（库默认值） */
    private java.time.LocalDateTime createdAt;

    /** 更新时间（库自动维护） */
    private java.time.LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFundCode() {
        return fundCode;
    }

    public void setFundCode(String fundCode) {
        this.fundCode = fundCode;
    }

    public LocalDate getTradeDate() {
        return tradeDate;
    }

    public void setTradeDate(LocalDate tradeDate) {
        this.tradeDate = tradeDate;
    }

    public java.math.BigDecimal getClosePrice() {
        return closePrice;
    }

    public void setClosePrice(java.math.BigDecimal closePrice) {
        this.closePrice = closePrice;
    }

    public java.math.BigDecimal getMa5() {
        return ma5;
    }

    public void setMa5(java.math.BigDecimal ma5) {
        this.ma5 = ma5;
    }

    public java.math.BigDecimal getMa10() {
        return ma10;
    }

    public void setMa10(java.math.BigDecimal ma10) {
        this.ma10 = ma10;
    }

    public java.math.BigDecimal getMa20() {
        return ma20;
    }

    public void setMa20(java.math.BigDecimal ma20) {
        this.ma20 = ma20;
    }

    public java.math.BigDecimal getMa30() {
        return ma30;
    }

    public void setMa30(java.math.BigDecimal ma30) {
        this.ma30 = ma30;
    }

    public java.math.BigDecimal getMa60() {
        return ma60;
    }

    public void setMa60(java.math.BigDecimal ma60) {
        this.ma60 = ma60;
    }

    public java.math.BigDecimal getMa90() {
        return ma90;
    }

    public void setMa90(java.math.BigDecimal ma90) {
        this.ma90 = ma90;
    }

    public java.math.BigDecimal getMa120() {
        return ma120;
    }

    public void setMa120(java.math.BigDecimal ma120) {
        this.ma120 = ma120;
    }

    public java.math.BigDecimal getMa250() {
        return ma250;
    }

    public void setMa250(java.math.BigDecimal ma250) {
        this.ma250 = ma250;
    }

    public java.time.LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(java.time.LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public java.time.LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(java.time.LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
