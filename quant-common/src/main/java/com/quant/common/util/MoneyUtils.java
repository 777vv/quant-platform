package com.quant.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 金额精度工具：金额 2 位、价格/净值 4 位（HALF_UP，技术文档非功能需求"数据准确性"）
 */
public final class MoneyUtils {

    private MoneyUtils() {
    }

    /** 字符串转 BigDecimal，空值/空白返回 0 */
    public static BigDecimal of(String value) {
        return value == null || value.isBlank() ? BigDecimal.ZERO : new BigDecimal(value);
    }

    /** 按指定小数位四舍五入（HALF_UP），null 按 0 处理 */
    public static BigDecimal scale(BigDecimal value, int scale) {
        return nvl(value).setScale(scale, RoundingMode.HALF_UP);
    }

    /** 保留 2 位小数（金额） */
    public static BigDecimal scale2(BigDecimal value) {
        return scale(value, 2);
    }

    /** 保留 4 位小数（价格/净值） */
    public static BigDecimal scale4(BigDecimal value) {
        return scale(value, 4);
    }

    /** null 安全：null 转 0 */
    public static BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** 是否为 null 或 0 */
    public static boolean isNullOrZero(BigDecimal value) {
        return value == null || value.compareTo(BigDecimal.ZERO) == 0;
    }
}
