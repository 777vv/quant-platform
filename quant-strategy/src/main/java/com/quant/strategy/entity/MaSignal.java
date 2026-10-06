package com.quant.strategy.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 均线信号记录（V5.70）：短期均线上穿/下穿长期均线时记录一条。
 * 幂等键 = fund_code + signal_date + (ma_short, ma_long) 均线对；每日 10:00 定时任务增量判定。
 */
@TableName("ma_signal")
public class MaSignal {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 基金代码 */
    private String fundCode;

    /** 信号日期（交叉确认的交易日） */
    private LocalDate signalDate;

    /** 短期均线周期（天） */
    private Integer maShort;

    /** 长期均线周期（天） */
    private Integer maLong;

    /** 方向：UP=上穿（金叉） DOWN=下穿（死叉） */
    private String direction;

    /** 信号日收盘价/净值 */
    private BigDecimal priceAt;

    /** 信号日短期均线值 */
    private BigDecimal maShortVal;

    /** 信号日长期均线值 */
    private BigDecimal maLongVal;

    /** 创建时间（库默认值） */
    private java.time.LocalDateTime createdAt;

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

    public LocalDate getSignalDate() {
        return signalDate;
    }

    public void setSignalDate(LocalDate signalDate) {
        this.signalDate = signalDate;
    }

    public Integer getMaShort() {
        return maShort;
    }

    public void setMaShort(Integer maShort) {
        this.maShort = maShort;
    }

    public Integer getMaLong() {
        return maLong;
    }

    public void setMaLong(Integer maLong) {
        this.maLong = maLong;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public BigDecimal getPriceAt() {
        return priceAt;
    }

    public void setPriceAt(BigDecimal priceAt) {
        this.priceAt = priceAt;
    }

    public BigDecimal getMaShortVal() {
        return maShortVal;
    }

    public void setMaShortVal(BigDecimal maShortVal) {
        this.maShortVal = maShortVal;
    }

    public BigDecimal getMaLongVal() {
        return maLongVal;
    }

    public void setMaLongVal(BigDecimal maLongVal) {
        this.maLongVal = maLongVal;
    }

    public java.time.LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(java.time.LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
