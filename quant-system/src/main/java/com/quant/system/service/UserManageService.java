package com.quant.system.service;

import java.util.List;

import com.quant.system.dto.PermissionOptionVO;
import com.quant.system.dto.UserCreateRequest;
import com.quant.system.dto.UserManageVO;
import com.quant.system.dto.UserPasswordResetRequest;
import com.quant.system.dto.UserStatusRequest;
import com.quant.system.dto.UserUpdateRequest;

/**
 * 用户管理服务（V5.58，仅管理员）：临时账号的创建/编辑/启停/删除/重置密码与权限码字典。
 */
public interface UserManageService {

    /** 全部账号列表（含管理员行，仅展示） */
    List<UserManageVO> list();

    /** 权限码字典（分组返回，供勾选面板渲染） */
    List<PermissionOptionVO> permissionOptions();

    /** 创建临时账号（角色固定 GUEST） */
    UserManageVO create(UserCreateRequest request);

    /** 编辑临时账号（权限/昵称/有效期/备注；用户名与角色不可改） */
    UserManageVO update(Long id, UserUpdateRequest request);

    /** 启用/停用：停用立即踢下线 */
    void updateStatus(Long id, UserStatusRequest request);

    /** 重置密码：新密码生效后该账号全端下线 */
    void resetPassword(Long id, UserPasswordResetRequest request);

    /** 删除账号（先踢下线再删行） */
    void delete(Long id);
}
