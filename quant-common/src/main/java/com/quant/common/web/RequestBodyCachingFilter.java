package com.quant.common.web;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingRequestWrapper;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 请求体缓存过滤器（配合 InvokeLogInterceptor 输出 JSON 请求参数日志）：
 * 仅对 JSON 请求用 ContentCachingRequestWrapper 包装——包装器在控制器读取请求体时同步缓存字节，
 * 拦截器在请求结束后即可读取缓存内容做日志，且不影响控制器正常消费请求体（可重复读）。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestBodyCachingFilter implements Filter {

    /** 判断是否 JSON 请求（与拦截器的判定保持一致） */
    static boolean isJsonRequest(ServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null && contentType.toLowerCase().contains("application/json");
    }

    /** 请求体缓存上限（字节）：仅服务于日志，超出部分不缓存（日志本身还会二次截断） */
    private static final int CACHE_LIMIT_BYTES = 64 * 1024;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest httpRequest && isJsonRequest(httpRequest)) {
            chain.doFilter(new ContentCachingRequestWrapper(httpRequest, CACHE_LIMIT_BYTES), response);
            return;
        }
        chain.doFilter(request, response);
    }
}
