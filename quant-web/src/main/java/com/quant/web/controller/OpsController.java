package com.quant.web.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.quant.common.result.R;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运维接口（V6.66 用户要求）：提供**无需登录**的 GET 接口手动触发一次 GC。
 *
 * <p>⚠️ 安全约定：本接口在 {@code SaTokenConfig} 中显式加入免鉴权白名单——**这是刻意的运维入口**。
 * 为防止被外部扫描/刷接口造成 GC 风暴（每次都触发 Full GC 会拖慢所有请求），
 * 这里做了**最小间隔 30 秒**的限流（CAS 保证并发下也只放行一次），并记录调用来源 IP 与释放内存，
 * 便于事后排查。生产环境建议在反向代理层再把 {@code /api/ops/gc} 限制为内网或加来源白名单。
 */
@RestController
@RequestMapping("/api/ops")
public class OpsController {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpsController.class);

    /** 两次手动 GC 的最小间隔（毫秒）：30 秒，防止被刷接口导致 GC 风暴 */
    private static final long MIN_INTERVAL_MS = 30_000L;

    /** 上次触发时间戳（毫秒）；0 表示尚未触发过 */
    private static final AtomicLong LAST_GC_AT = new AtomicLong(0L);

    /**
     * 手动触发一次 GC（GET，无需鉴权）。
     *
     * @param request 用于记录调用来源 IP
     * @return 是否真正触发、前后堆内存（MB）、释放量、耗时；限流命中时返回 triggered=false 与需等待秒数
     */
    @GetMapping("/gc")
    public R<Map<String, Object>> gc(HttpServletRequest request) {
        long now = System.currentTimeMillis();
        long last = LAST_GC_AT.get();
        long elapsed = now - last;
        if (last != 0L && elapsed < MIN_INTERVAL_MS) {
            Map<String, Object> skipped = new LinkedHashMap<>();
            skipped.put("triggered", false);
            skipped.put("reason", "调用过于频繁：两次手动 GC 最小间隔 " + (MIN_INTERVAL_MS / 1000) + " 秒");
            skipped.put("waitSeconds", (MIN_INTERVAL_MS - elapsed + 999) / 1000);
            LOGGER.info("手动GC跳过（距上次 {} ms，来源 {}）", elapsed, clientIp(request));
            return R.ok(skipped);
        }
        // CAS 抢占：并发调用时只放行一个，其余按"过于频繁"返回（避免同时触发多次 GC）
        if (!LAST_GC_AT.compareAndSet(last, now)) {
            Map<String, Object> skipped = new LinkedHashMap<>();
            skipped.put("triggered", false);
            skipped.put("reason", "已有并发的 GC 请求正在处理");
            skipped.put("waitSeconds", MIN_INTERVAL_MS / 1000);
            return R.ok(skipped);
        }

        Runtime runtime = Runtime.getRuntime();
        long beforeUsed = runtime.totalMemory() - runtime.freeMemory();
        long start = System.nanoTime();
        System.gc();
        long costMs = (System.nanoTime() - start) / 1_000_000L;
        long afterUsed = runtime.totalMemory() - runtime.freeMemory();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("triggered", true);
        result.put("beforeUsedMb", toMb(beforeUsed));
        result.put("afterUsedMb", toMb(afterUsed));
        result.put("freedMb", toMb(Math.max(0L, beforeUsed - afterUsed)));
        result.put("totalMb", toMb(runtime.totalMemory()));
        result.put("maxMb", toMb(runtime.maxMemory()));
        result.put("costMs", costMs);
        LOGGER.info("手动GC触发：来源[{}]，堆 {}MB → {}MB（释放 {}MB），耗时 {}ms",
                clientIp(request), toMb(beforeUsed), toMb(afterUsed),
                toMb(Math.max(0L, beforeUsed - afterUsed)), costMs);
        return R.ok(result);
    }

    /** 字节 → MB（保留 1 位小数） */
    private double toMb(long bytes) {
        return Math.round(bytes / 1024.0 / 1024.0 * 10.0) / 10.0;
    }

    /** 调用来源 IP：优先反向代理头（与登录日志同款取法），都没有时用 RemoteAddr */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        return realIp != null && !realIp.isBlank() ? realIp.trim() : request.getRemoteAddr();
    }
}
