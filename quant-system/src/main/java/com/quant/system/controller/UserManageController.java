package com.quant.system.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.quant.common.result.R;
import com.quant.common.auth.PermissionCodes;
import com.quant.system.dto.PermissionOptionVO;
import com.quant.system.dto.UserCreateRequest;
import com.quant.system.dto.UserManageVO;
import com.quant.system.dto.UserPasswordResetRequest;
import com.quant.system.dto.UserStatusRequest;
import com.quant.system.dto.UserUpdateRequest;
import com.quant.system.service.UserManageService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户管理接口（V5.58，仅管理员）：临时账号的创建/编辑/启停/删除/重置密码与权限码字典。
 * 类级 @SaCheckRole 兜底——普通临时账号即使拿到 token 也进不来这些接口。
 */
@RestController
@RequestMapping("/api/user/manage")
@SaCheckRole(PermissionCodes.ROLE_ADMIN)
public class UserManageController {

    private final UserManageService userManageService;

    public UserManageController(UserManageService userManageService) {
        this.userManageService = userManageService;
    }

    /** 全部账号列表（含管理员行，仅展示不可改） */
    @GetMapping("/list")
    public R<List<UserManageVO>> list() {
        return R.ok(userManageService.list());
    }

    /** 权限码字典（勾选面板数据源；含「只读访客」默认模板码） */
    @GetMapping("/permission-options")
    public R<List<PermissionOptionVO>> permissionOptions() {
        return R.ok(userManageService.permissionOptions());
    }

    /** 「一键只读访客」默认模板的权限码清单（前端建号弹窗一键填充用） */
    @GetMapping("/guest-default")
    public R<List<String>> guestDefault() {
        return R.ok(PermissionCodes.GUEST_DEFAULT);
    }

    /** 创建临时账号（角色固定 GUEST） */
    @PostMapping
    public R<UserManageVO> create(@Valid @RequestBody UserCreateRequest request) {
        return R.ok(userManageService.create(request));
    }

    /** 编辑临时账号（权限/昵称/有效期/备注；改完即时生效） */
    @PutMapping("/{id}")
    public R<UserManageVO> update(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest request) {
        return R.ok(userManageService.update(id, request));
    }

    /** 启用/停用（停用立即踢下线） */
    @PutMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody UserStatusRequest request) {
        userManageService.updateStatus(id, request);
        return R.ok();
    }

    /** 重置密码（重置后该账号全端下线） */
    @PutMapping("/{id}/password")
    public R<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody UserPasswordResetRequest request) {
        userManageService.resetPassword(id, request);
        return R.ok();
    }

    /** 删除账号（先踢下线再删行） */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        userManageService.delete(id);
        return R.ok();
    }
}
