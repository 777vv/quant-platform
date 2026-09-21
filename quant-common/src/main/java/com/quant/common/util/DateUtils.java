package com.quant.common.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 日期工具类
 */
public final class DateUtils {

    /** yyyy-MM-dd */
    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** yyyy-MM-dd HH:mm:ss */
    public static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** yyyyMMdd（东财接口 beg/end 参数格式） */
    public static final DateTimeFormatter COMPACT_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private DateUtils() {
    }

    /** 今天 */
    public static LocalDate today() {
        return LocalDate.now();
    }

    /** 当前时间 */
    public static LocalDateTime now() {
        return LocalDateTime.now();
    }

    /** LocalDate -> "yyyy-MM-dd"，null 返回空串 */
    public static String format(LocalDate date) {
        return date == null ? "" : DATE.format(date);
    }

    /** LocalDateTime -> "yyyy-MM-dd HH:mm:ss"，null 返回空串 */
    public static String format(LocalDateTime dateTime) {
        return dateTime == null ? "" : DATETIME.format(dateTime);
    }

    /** "yyyy-MM-dd" -> LocalDate */
    public static LocalDate parse(String text) {
        return LocalDate.parse(text, DATE);
    }

    /** LocalDate -> yyyyMMdd 整型（东财接口 beg/end 参数用） */
    public static int toCompact(LocalDate date) {
        return Integer.parseInt(COMPACT_DATE.format(date));
    }
}
