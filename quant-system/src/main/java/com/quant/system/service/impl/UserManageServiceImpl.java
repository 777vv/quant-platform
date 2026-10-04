package com.quant.system.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import cn.dev33.satoken.secure.BCrypt;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.quant.common.exception.BizException;
import com.quant.common.auth.PermissionCodes;
import com.quant.system.dto.PermissionOptionVO;
import com.quant.system.dto.UserCreateRequest;
import com.quant.system.dto.UserManageVO;
import com.quant.system.dto.UserPasswordResetRequest;
import com.quant.system.dto.UserStatusRequest;
import com.quant.system.dto.UserUpdateRequest;
import com.quant.system.entity.SysUser;
import com.quant.system.mapper.SysUserMapper;
import com.quant.system.service.UserManageService;
import org.springframework.stereotype.Service;

/**
 * 用户管理服务实现（V5.58，仅管理员）。
 *
 * <p>保护口径：role=ADMIN 的行（含内置管理员）一律不可编辑/启停/删除/重置密码——
 * 临时账号管理只作用于 GUEST；创建出来的账号角色也恒为 GUEST，管理员只能由初始化器产生。
 */
@Service
public class UserManageServiceImpl implements UserManageService {

    private final SysUserMapper sysUserMapper;

    public UserManageServiceImpl(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    public List<UserManageVO> list() {
        List<SysUser> users = sysUserMapper.selectList(
                new LambdaQueryWrapper<SysUser>().orderByAsc(SysUser::getId));
        List<UserManageVO> vos = new ArrayList<>();
        for (SysUser user : users) {
            vos.add(toVo(user));
        }
        return vos;
    }

    @Override
    public List<PermissionOptionVO> permissionOptions() {
        List<PermissionOptionVO> options = new ArrayList<>();
        for (String code : PermissionCodes.ALL) {
            PermissionOptionVO option = new PermissionOptionVO();
            option.setCode(code);
            option.setLabel(PermissionCodes.labelOf(code));
            option.setGroup(PermissionCodes.groupOf(code));
            option.setGroupLabel("menu".equals(PermissionCodes.groupOf(code)) ? "可见菜单" : "允许操作");
            options.add(option);
        }
        return options;
    }

    @Override
    public UserManageVO create(UserCreateRequest request) {
        Long exists = sysUserMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, request.getUsername()));
        if (exists != null && exists > 0) {
            throw new BizException("用户名已存在：" + request.getUsername());
        }
        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setPassword(BCrypt.hashpw(request.getPassword(), BCrypt.gensalt()));
        user.setNickname(request.getNickname() == null || request.getNickname().isBlank()
                ? request.getUsername() : request.getNickname());
        user.setRole(PermissionCodes.ROLE_GUEST);
        user.setPermissions(joinCodes(request.getPermissions()));
        user.setEnabled(1);
        user.setFailCount(0);
        user.setExpiresAt(toDateTime(request.getExpiresOn()));
        user.setRemark(request.getRemark());
        sysUserMapper.insert(user);
        return toVo(user);
    }

    @Override
    public UserManageVO update(Long id, UserUpdateRequest request) {
        SysUser user = requireGuest(id);
        if (request.getNickname() != null) {
            user.setNickname(request.getNickname().isBlank() ? user.getUsername() : request.getNickname());
        }
        if (request.getPermissions() != null) {
            user.setPermissions(joinCodes(request.getPermissions()));
        }
        user.setExpiresAt(toDateTime(request.getExpiresOn()));
        if (request.getRemark() != null) {
            user.setRemark(request.getRemark());
        }
        sysUserMapper.updateById(user);
        // 权限每次校验都查库，改完即时生效，无需强制下线
        return toVo(user);
    }

    @Override
    public void updateStatus(Long id, UserStatusRequest request) {
        SysUser user = requireGuest(id);
        user.setEnabled(request.getEnabled());
        sysUserMapper.updateById(user);
        if (request.getEnabled() != null && request.getEnabled() == 0) {
            // 停用立即踢下线：对方下一次请求即 401
            StpUtil.kickout(id);
        }
    }

    @Override
    public void resetPassword(Long id, UserPasswordResetRequest request) {
        SysUser user = requireGuest(id);
        user.setPassword(BCrypt.hashpw(request.getPassword(), BCrypt.gensalt()));
        sysUserMapper.updateById(user);
        // 密码被重置后旧会话不再可信，全端下线
        StpUtil.kickout(id);
    }

    @Override
    public void delete(Long id) {
        requireGuest(id);
        StpUtil.kickout(id);
        sysUserMapper.deleteById(id);
    }

    /** 取 GUEST 账号；不存在或角色为 ADMIN 都拒绝（管理员行不可在此维护） */
    private SysUser requireGuest(Long id) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BizException("账号不存在");
        }
        if (PermissionCodes.ROLE_ADMIN.equals(user.getRole())) {
            throw new BizException("管理员账号不可在用户管理中维护");
        }
        return user;
    }

    /** 权限码清单 → 逗号串：过滤非法码、去重（保存到 sys_user.permissions） */
    private String joinCodes(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return "";
        }
        Set<String> kept = new LinkedHashSet<>();
        for (String code : codes) {
            String trimmed = code == null ? "" : code.trim();
            if (!trimmed.isEmpty() && PermissionCodes.isKnown(trimmed)) {
                kept.add(trimmed);
            }
        }
        return String.join(",", kept);
    }

    /** 有效期到日期 → 当日 23:59:59（null=永久） */
    private LocalDateTime toDateTime(java.time.LocalDate expiresOn) {
        return expiresOn == null ? null : expiresOn.atTime(23, 59, 59);
    }

    private UserManageVO toVo(SysUser user) {
        UserManageVO vo = new UserManageVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setRole(user.getRole() == null ? PermissionCodes.ROLE_ADMIN : user.getRole());
        String stored = user.getPermissions();
        vo.setPermissions(stored == null || stored.isBlank()
                ? List.of() : Arrays.asList(stored.split(",")));
        vo.setEnabled(user.getEnabled());
        vo.setExpiresAt(user.getExpiresAt());
        vo.setRemark(user.getRemark());
        vo.setLastLoginAt(user.getLastLoginAt());
        vo.setCreatedAt(user.getCreatedAt());
        return vo;
    }
}
