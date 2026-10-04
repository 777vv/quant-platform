package com.quant.system.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 启用/停用临时账号请求（V5.58，仅管理员可用）。停用立即踢下线。
 */
@Data
public class UserStatusRequest {

    /** 1=启用 0=停用 */
    @NotNull(message = "enabled 不能为空")
    private Integer enabled;
}
