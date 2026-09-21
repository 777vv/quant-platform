package com.quant.strategy.dto;

import lombok.Data;

/**
 * 策略配置新增/修改请求
 */
@Data
public class StrategyConfigRequest {

    /** 策略类型（GRID/VAL_PERCENTILE） */
    private String strategyType;

    /** 策略展示名（空则用策略默认名） */
    private String strategyName;

    /** 策略参数（结构由各策略定义，保存前经 validateParams 校验） */
    private java.util.Map<String, Object> params;

    /** 1=启用 0=停用（null 保持原值） */
    private Integer enabled;
}
