package com.quant.strategy.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 策略配置（每基金每类型唯一）
 */
@Data
@TableName("strategy_config")
public class StrategyConfig {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 基金代码 */
    private String fundCode;

    /** GRID / VAL_PERCENTILE（每基金每类型唯一） */
    private String strategyType;

    /** 策略展示名 */
    private String strategyName;

    /** 参数 JSON 字符串（保存前经 Strategy.validateParams 校验） */
    private String params;

    /** 备注（用户自填，如建仓思路 / 调参缘由；V5.34 新增） */
    private String remark;

    /** 1=启用（每日信号计算）0=停用 */
    private Integer enabled;

    /** 创建时间（库默认值） */
    private LocalDateTime createdAt;

    /** 更新时间（库自动维护） */
    private LocalDateTime updatedAt;
}
