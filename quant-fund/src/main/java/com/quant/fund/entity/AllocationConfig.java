package com.quant.fund.entity;

import java.math.BigDecimal;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 全局仓位配置（V5.36，单行 id=1）：五类资产的市值占比目标范围（%）。
 *
 * <p>每周二 09:00 的仓位检查任务把当前组合与这里的范围比对——
 * 任一类别越界即发告警（邮件+微信），全部在范围内则不动作。
 *
 * <p>分类口径（按基金标签名匹配，标签在【基金池 → 标签库】维护）：
 * 现金 = 现金余额 + 标签「现金」的基金市值（如债基）；A/美/亚太/欧洲 = 打了对应标签的基金市值。
 * 一只基金打了多个类别标签会重复计入对应桶（配置标签时注意别重叠）。
 */
@Data
@TableName("allocation_config")
public class AllocationConfig {

    /** 固定主键（单行配置，值恒为 1） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 是否启用每周二检查（0=停用，任务空转） */
    private Integer enabled;

    /** 现金比例下限（%） */
    private BigDecimal cashMin;

    /** 现金比例上限（%） */
    private BigDecimal cashMax;

    /** A股比例下限（%） */
    private BigDecimal aShareMin;

    /** A股比例上限（%） */
    private BigDecimal aShareMax;

    /** 美股比例下限（%） */
    private BigDecimal usMin;

    /** 美股比例上限（%） */
    private BigDecimal usMax;

    /** 亚太比例下限（%） */
    private BigDecimal asiaMin;

    /** 亚太比例上限（%） */
    private BigDecimal asiaMax;

    /** 欧洲比例下限（%） */
    private BigDecimal euMin;

    /** 欧洲比例上限（%） */
    private BigDecimal euMax;

    /** 最近修改时间 */
    private java.time.LocalDateTime updatedAt;
}
