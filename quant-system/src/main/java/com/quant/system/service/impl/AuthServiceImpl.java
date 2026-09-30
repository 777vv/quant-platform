package com.quant.system.service.impl;

import java.time.LocalDateTime;

import cn.dev33.satoken.secure.BCrypt;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.quant.common.exception.BizException;
import com.quant.system.dto.LoginRequest;
import com.quant.system.dto.LoginResult;
import com.quant.system.dto.PasswordRequest;
import com.quant.system.dto.UserVO;
import com.quant.system.entity.SysUser;
import com.quant.system.mapper.SysUserMapper;
import com.quant.system.service.AuthService;
import com.quant.system.service.LoginLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 认证服务实现：登录防爆破（连续失败 5 次锁定 10 分钟）、改密全端下线
 */
@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthServiceImpl.class);

    private static final int MAX_FAIL_COUNT = 5;

    private static final int LOCK_MINUTES = 10;

    private static final String DEFAULT_MSG = "用户名或密码错误";

    private final SysUserMapper sysUserMapper;

    /** 登录日志（V5.46）：四条路径（用户不存在/账号锁定/密码错误/成功）各记一行 */
    private final LoginLogService loginLogService;

    public AuthServiceImpl(SysUserMapper sysUserMapper, LoginLogService loginLogService) {
        this.sysUserMapper = sysUserMapper;
        this.loginLogService = loginLogService;
    }

    @Override
    public LoginResult login(LoginRequest request) {
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, request.getUsername()));
        if (user == null) {
            // 用户不存在也记一行：便于识别撞库尝试与自己输错用户名的场景
            loginLogService.record(request.getUsername(), false, DEFAULT_MSG);
            throw new BizException(DEFAULT_MSG);
        }
        checkLocked(user);
        if (!BCrypt.checkpw(request.getPassword(), user.getPassword())) {
            recordLoginFail(user);
            loginLogService.record(request.getUsername(), false, DEFAULT_MSG);
            throw new BizException(DEFAULT_MSG);
        }
        recordLoginSuccess(user.getId());
        StpUtil.login(user.getId());
        // 成功日志放在 StpUtil.login 之后：至此才算真正登录成功
        loginLogService.record(request.getUsername(), true, null);

        LoginResult result = new LoginResult();
        result.setToken(StpUtil.getTokenValue());
        result.setUser(toVo(user));
        return result;
    }

    @Override
    public void logout() {
        StpUtil.logout();
    }

    @Override
    public UserVO currentUser() {
        long userId = StpUtil.getLoginIdAsLong();
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        return toVo(user);
    }

    @Override
    public void changePassword(PasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BizException("两次输入的新密码不一致");
        }
        long userId = StpUtil.getLoginIdAsLong();
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || !BCrypt.checkpw(request.getOldPassword(), user.getPassword())) {
            throw new BizException("原密码错误");
        }
        sysUserMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .set(SysUser::getPassword, BCrypt.hashpw(request.getNewPassword(), BCrypt.gensalt())));
        StpUtil.logout(userId);
        LOGGER.info("用户[{}]修改密码，全部会话已下线", userId);
    }

    private void checkLocked(SysUser user) {
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            long minutes = java.time.Duration.between(LocalDateTime.now(), user.getLockedUntil()).toMinutes() + 1;
            String reason = "账号已锁定（约 " + minutes + " 分钟后可重试）";
            loginLogService.record(user.getUsername(), false, reason);
            throw new BizException("失败次数过多，账号已锁定，请约 " + minutes + " 分钟后重试");
        }
    }

    private void recordLoginFail(SysUser user) {
        int failCount = (user.getFailCount() == null ? 0 : user.getFailCount()) + 1;
        if (failCount >= MAX_FAIL_COUNT) {
            sysUserMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                    .eq(SysUser::getId, user.getId())
                    .set(SysUser::getFailCount, 0)
                    .set(SysUser::getLockedUntil, LocalDateTime.now().plusMinutes(LOCK_MINUTES)));
            LOGGER.warn("用户[{}]连续登录失败{}次，锁定{}分钟", user.getUsername(), failCount, LOCK_MINUTES);
        } else {
            sysUserMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                    .eq(SysUser::getId, user.getId())
                    .set(SysUser::getFailCount, failCount));
        }
    }

    private void recordLoginSuccess(Long userId) {
        sysUserMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .set(SysUser::getFailCount, 0)
                .set(SysUser::getLockedUntil, null)
                .set(SysUser::getLastLoginAt, LocalDateTime.now()));
    }

    private UserVO toVo(SysUser user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setEmail(user.getEmail());
        vo.setLastLoginAt(user.getLastLoginAt());
        return vo;
    }
}
