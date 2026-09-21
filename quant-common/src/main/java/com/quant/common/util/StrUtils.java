package com.quant.common.util;

/**
 * 字符串工具类
 */
public final class StrUtils {

    private StrUtils() {
    }

    /** 是否为 null/空白 */
    public static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    /** 是否非 null 且非空白 */
    public static boolean isNotBlank(String text) {
        return !isBlank(text);
    }

    /** 空白时返回默认值 */
    public static String orDefault(String text, String defaultValue) {
        return isBlank(text) ? defaultValue : text;
    }

    /** 空白转 null（外部接口字段清洗用） */
    public static String blankToNull(String text) {
        return isBlank(text) ? null : text;
    }
}
