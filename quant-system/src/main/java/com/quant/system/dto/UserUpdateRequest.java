package com.quant.system.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 编辑临时账号请求（V5.58，仅管理员可用）：权限/昵称/有效期/备注。用户名与角色不可改。
 */
@Data
public class UserUpdateRequest {

    /** 昵称（可空=不改） */
    @Size(max = 32, message = "昵称不能超过 32 位")
    private String nickname;

    /** 权限码清单（null=不改；非法码会被过滤） */
    private List<String> permissions;

    /** 有效期至（当日 23:59:59 失效；null=改为永久） */
    private LocalDate expiresOn;

    /** 备注 */
    @Size(max = 255, message = "备注不能超过 255 字")
    private String remark;
}
