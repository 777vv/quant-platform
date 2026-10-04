package com.quant.system.auth;

import cn.dev33.satoken.stp.StpInterface;
import com.quant.common.auth.PermissionCodes;
import com.quant.system.entity.SysUser;
import com.quant.system.mapper.SysUserMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Sa-Token 权限数据源（V5.58）：注解 @SaCheckPermission / @SaCheckRole 校验时由此提供当前用户的权限码与角色。
 *
 * <p>口径：ADMIN 恒为全部权限码（含未来新增码，无需改库）；GUEST 只有所存 permissions 里的码。
 * 每次校验按主键查一次 sys_user（单用户量级，代价可忽略），好处是管理员改完权限立即生效、无需重新登录。
 */
@Component
public class SaTokenPermissionImpl implements StpInterface {

    private final SysUserMapper sysUserMapper;

    public SaTokenPermissionImpl(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    /** 返回当前登录用户的权限码列表（@SaCheckPermission 校验数据源） */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        SysUser user = sysUserMapper.selectById(Long.valueOf(loginId.toString()));
        if (user == null) {
            return List.of();
        }
        if (PermissionCodes.ROLE_ADMIN.equals(user.getRole())) {
            return PermissionCodes.ALL;
        }
        List<String> codes = new ArrayList<>();
        String stored = user.getPermissions();
        if (stored != null && !stored.isBlank()) {
            for (String code : stored.split(",")) {
                String trimmed = code.trim();
                // 只放行已知码：库里出现未知码（手改库）不生效
                if (!trimmed.isEmpty() && PermissionCodes.isKnown(trimmed)) {
                    codes.add(trimmed);
                }
            }
        }
        return codes;
    }

    /** 返回当前登录用户的角色列表（@SaCheckRole 校验数据源） */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        SysUser user = sysUserMapper.selectById(Long.valueOf(loginId.toString()));
        if (user == null) {
            return List.of();
        }
        String role = user.getRole() == null ? PermissionCodes.ROLE_ADMIN : user.getRole();
        return List.of(role);
    }
}
