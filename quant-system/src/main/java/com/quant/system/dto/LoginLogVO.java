package com.quant.system.dto;

import java.time.LocalDateTime;

/**
 * 登录日志行（前端列表用）
 */
public record LoginLogVO(
        /** 主键 */
        Long id,
        /** 登录用户名 */
        String username,
        /** 是否成功（true=登录成功） */
        boolean success,
        /** 失败原因（成功时为 null） */
        String failReason,
        /** 客户端 IP */
        String ip,
        /** IP 归属地（内网/本机等） */
        String ipLocation,
        /** 客户端 UA */
        String userAgent,
        /** 登录时间 */
        LocalDateTime createdAt) {
}
