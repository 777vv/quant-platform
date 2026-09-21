package com.quant.common.util;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * JSON 工具类（独立于 Spring 上下文的静态实例）。
 * Jackson 3：异常均为非受检、未知属性默认容忍，无需额外配置。
 */
public final class JsonUtils {

    /** 全局静态映射器（线程安全） */
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private JsonUtils() {
    }

    /** 对象 -> JSON 字符串；失败抛 IllegalArgumentException */
    public static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("JSON序列化失败", e);
        }
    }

    /** JSON 字符串 -> 对象；失败抛 IllegalArgumentException */
    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return MAPPER.readValue(json, clazz);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("JSON反序列化失败: " + clazz.getSimpleName(), e);
        }
    }

    /** 暴露静态映射器（东财 client 解析 JsonNode 用） */
    public static ObjectMapper mapper() {
        return MAPPER;
    }
}
