package com.quant.fund.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * 全局仓位配置保存请求（V5.36，平台配置 → 仓位配置卡）。
 * 五组比例均为总市值的百分比（0~100），min ≤ max。
 *
 * @param enabled  是否启用每周二检查
 * @param cashMin  现金比例下限
 * @param cashMax  现金比例上限
 * @param aShareMin A股比例下限
 * @param aShareMax A股比例上限
 * @param usMin    美股比例下限
 * @param usMax    美股比例上限
 * @param asiaMin  亚太比例下限
 * @param asiaMax  亚太比例上限
 * @param euMin    欧洲比例下限
 * @param euMax    欧洲比例上限
 */
public record AllocationConfigRequest(
        Boolean enabled,
        @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.DecimalMin("0") @jakarta.validation.constraints.DecimalMax("100") BigDecimal cashMin,
        @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.DecimalMin("0") @jakarta.validation.constraints.DecimalMax("100") BigDecimal cashMax,
        @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.DecimalMin("0") @jakarta.validation.constraints.DecimalMax("100") BigDecimal aShareMin,
        @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.DecimalMin("0") @jakarta.validation.constraints.DecimalMax("100") BigDecimal aShareMax,
        @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.DecimalMin("0") @jakarta.validation.constraints.DecimalMax("100") BigDecimal usMin,
        @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.DecimalMin("0") @jakarta.validation.constraints.DecimalMax("100") BigDecimal usMax,
        @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.DecimalMin("0") @jakarta.validation.constraints.DecimalMax("100") BigDecimal asiaMin,
        @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.DecimalMin("0") @jakarta.validation.constraints.DecimalMax("100") BigDecimal asiaMax,
        @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.DecimalMin("0") @jakarta.validation.constraints.DecimalMax("100") BigDecimal euMin,
        @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.DecimalMin("0") @jakarta.validation.constraints.DecimalMax("100") BigDecimal euMax) {
}
