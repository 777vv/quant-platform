package com.quant.common.log;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.quant.common.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingRequestWrapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 接口调用日志拦截器（参照 RuoYi PlusWebInvokeTimeInterceptor，V1.9 ㊿）：
 * 进入控制器时打印 URL、来源与查询参数并开始计时；请求结束后打印耗时、响应状态与 JSON 请求体
 * （敏感字段脱敏、超长截断）。请求体依赖 RequestBodyCachingFilter 的缓存包装，在结束时统一输出。
 */
public class InvokeLogInterceptor implements HandlerInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(InvokeLogInterceptor.class);

    /** 计时起点在请求对象上的属性键 */
    private static final String ATTR_START_NANOS = InvokeLogInterceptor.class.getName() + ".startNanos";

    /** 参数/请求体日志最大长度（超出截断） */
    private static final int MAX_LOG_LENGTH = 2000;

    /** 敏感字段名片段（键名命中即脱敏为 ***） */
    private static final String[] SENSITIVE_KEY_PARTS = {
            "password", "pwd", "token", "secret", "apikey", "api_key", "api-key", "authorization", "credential"
    };

    /**
     * 控制器调用前：记录开始时间并输出入口日志（URL、来源 IP、查询参数）。
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(ATTR_START_NANOS, System.nanoTime());
        String url = request.getMethod() + " " + request.getRequestURI();
        String query = request.getQueryString();
        LOGGER.info("[WEB]开始请求 => URL[{}],来源[{}],查询参数[{}]",
                url, request.getRemoteAddr(), query == null ? "无" : limit(query));
        return true;
    }

    /**
     * 请求完成后：输出耗时、状态码与脱敏后的 JSON 请求体（仅 JSON 请求且已被包装时读取缓存）。
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception ex) {
        Object startNanos = request.getAttribute(ATTR_START_NANOS);
        if (startNanos == null) {
            return;
        }
        long elapsedMs = (System.nanoTime() - (Long) startNanos) / 1_000_000L;
        String url = request.getMethod() + " " + request.getRequestURI();
        String body = request instanceof ContentCachingRequestWrapper wrapper
                ? limit(sanitize(wrapper.getContentAsByteArray())) : "非JSON";
        LOGGER.info("[WEB]结束请求 => URL[{}],耗时[{}ms],状态[{}],请求体[{}]{}",
                url, elapsedMs, response.getStatus(), body,
                ex != null ? ",异常[" + ex.getMessage() + "]" : "");
    }

    /** 缓存字节 → 脱敏字符串；解析失败按原样输出（不影响主流程） */
    private String sanitize(byte[] content) {
        if (content == null || content.length == 0) {
            return "";
        }
        String raw = new String(content, StandardCharsets.UTF_8);
        try {
            JsonNode root = JsonUtils.mapper().readTree(raw);
            if (root instanceof ObjectNode obj) {
                maskSensitive(obj);
                return obj.toString();
            }
            return raw;
        } catch (Exception e) {
            return raw;
        }
    }

    /** 递归脱敏：键名命中敏感片段的叶子值替换为 *** */
    private void maskSensitive(ObjectNode node) {
        List<String> names = new ArrayList<>();
        node.properties().forEach(entry -> names.add(entry.getKey()));
        for (String key : names) {
            JsonNode value = node.get(key);
            if (isSensitiveKey(key) && value != null && value.isValueNode()) {
                node.put(key, "***");
            } else if (value instanceof ObjectNode child) {
                maskSensitive(child);
            }
        }
    }

    private boolean isSensitiveKey(String key) {
        String lower = key.toLowerCase();
        for (String part : SENSITIVE_KEY_PARTS) {
            if (lower.contains(part)) {
                return true;
            }
        }
        return false;
    }

    private String limit(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= MAX_LOG_LENGTH ? value : value.substring(0, MAX_LOG_LENGTH) + "...(截断)";
    }
}
