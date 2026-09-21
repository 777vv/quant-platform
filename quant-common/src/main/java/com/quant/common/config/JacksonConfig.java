package com.quant.common.config;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.ext.javatime.deser.LocalDateDeserializer;
import tools.jackson.databind.ext.javatime.deser.LocalDateTimeDeserializer;
import tools.jackson.databind.ext.javatime.deser.LocalTimeDeserializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateSerializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;
import tools.jackson.databind.ext.javatime.ser.LocalTimeSerializer;
import tools.jackson.databind.module.SimpleModule;

/**
 * Jackson 序列化统一配置（Spring Boot 4 = Jackson 3，tools.jackson 命名空间）：
 * 日期时间按固定格式字符串输出
 */
@Configuration
public class JacksonConfig {

    @Bean
    public JsonMapperBuilderCustomizer jacksonCustomizer() {
        DateTimeFormatter datetime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        DateTimeFormatter date = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        DateTimeFormatter time = DateTimeFormatter.ofPattern("HH:mm:ss");
        SimpleModule module = new SimpleModule("quant-datetime");
        module.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(datetime));
        module.addSerializer(LocalDate.class, new LocalDateSerializer(date));
        module.addSerializer(LocalTime.class, new LocalTimeSerializer(time));
        module.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(datetime));
        module.addDeserializer(LocalDate.class, new LocalDateDeserializer(date));
        module.addDeserializer(LocalTime.class, new LocalTimeDeserializer(time));
        return builder -> builder.addModule(module);
    }
}
