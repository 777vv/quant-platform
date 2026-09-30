package com.quant.common.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 客户端 IP 提取（V5.46）：登录日志/审计用。
 *
 * <p>部署在 1Panel + Nginx 反向代理后面时，{@code getRemoteAddr()} 拿到的是**代理自身的内网地址**
 * （如 172.x.x.x），真实来源在代理写入的请求头里。取值优先级：
 * ① {@code X-Forwarded-For} 的第一个非 unknown 地址（代理逐跳追加，第一个是客户端）；
 * ② {@code X-Real-IP}；③ {@code RemoteAddr}（无代理时的直连地址）。
 *
 * <p><b>注意</b>：这些头可被客户端伪造，因此仅用于日志展示与排查，**不得作为安全判定依据**
 * （登录防爆破仍按账号计数，与 IP 无关）。
 */
public final class ClientIpUtils {

    /** 反代链路长度上限（防御超长伪造头） */
    private static final int MAX_HEADER_LENGTH = 256;

    private ClientIpUtils() {
    }

    /**
     * 取客户端真实 IP（取不到返回 null）；结果是原始地址字符串，IPv6 也可能是 {@code 0:0:0:0:0:0:0:1} 形式。
     */
    public static String clientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.length() > MAX_HEADER_LENGTH
                    ? forwarded.substring(0, MAX_HEADER_LENGTH) : forwarded;
            for (String part : first.split(",")) {
                String ip = part.trim();
                if (!ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                    return ip;
                }
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank() && !"unknown".equalsIgnoreCase(realIp.trim())) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    /** 取客户端 UA（超长截断，与 login_log.user_agent 列宽一致） */
    public static String userAgent(HttpServletRequest request, int maxLength) {
        if (request == null) {
            return null;
        }
        String ua = request.getHeader("User-Agent");
        if (ua == null || ua.isBlank()) {
            return null;
        }
        return ua.length() > maxLength ? ua.substring(0, maxLength) : ua;
    }

    /**
     * 是否内网/本机地址（IPv4 私有段、回环、链路本地、IPv6 回环与唯一本地地址）。
     * 内网地址无需解析归属地，直接展示"内网"更直观。
     */
    public static boolean isPrivateIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        String value = ip.trim();
        if ("0:0:0:0:0:0:0:1".equals(value) || "::1".equals(value)) {
            return true;
        }
        // IPv4 映射的 IPv6（如 ::ffff:192.168.1.1）
        if (value.startsWith("::ffff:")) {
            value = value.substring("::ffff:".length());
        }
        // IPv6 唯一本地地址 fc00::/7 与链路本地 fe80::/10
        String lower = value.toLowerCase();
        if (lower.startsWith("fc") || lower.startsWith("fd") || lower.startsWith("fe8")
                || lower.startsWith("fe9") || lower.startsWith("fea") || lower.startsWith("feb")) {
            return lower.contains(":");
        }
        String[] parts = value.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        try {
            int a = Integer.parseInt(parts[0]);
            int b = Integer.parseInt(parts[1]);
            if (a == 10 || a == 127) {
                return true;
            }
            if (a == 172 && b >= 16 && b <= 31) {
                return true;
            }
            if (a == 192 && b == 168) {
                return true;
            }
            return a == 169 && b == 254;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
