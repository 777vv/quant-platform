package com.quant.system.config;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import com.quant.system.entity.SysUser;
import com.quant.system.mapper.SysUserMapper;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.LocalDateTime;

/**
 * SaToken 鉴权配置：拦截全部 /api/**，仅放行登录接口（技术文档 6.7）。
 *
 * <p>V5.58 起在登录校验之后追加「临时账号状态复核」：每请求按主键查一次 sys_user，
 * 停用（enabled=0）或已过期（expires_at &lt; now）的账号立即销毁会话并以 401 拒绝——
 * 管理员停用/改过期时间后，对方下一次请求即被踢出，无需等 token 自然过期。
 * 按主键单行查询在单用户量级下代价可忽略。
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    private final SysUserMapper sysUserMapper;

    public SaTokenConfig(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> {
                    StpUtil.checkLogin();
                    checkAccountActive();
                }))
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/login");
    }

    /** 复核当前登录账号的启用状态与有效期：停用/过期/被删即销毁会话并按未登录处理 */
    private void checkAccountActive() {
        Object loginId = StpUtil.getLoginIdDefaultNull();
        if (loginId == null) {
            return;
        }
        SysUser user = sysUserMapper.selectById(Long.valueOf(loginId.toString()));
        boolean invalid = user == null
                || (user.getEnabled() != null && user.getEnabled() == 0)
                || (user.getExpiresAt() != null && user.getExpiresAt().isBefore(LocalDateTime.now()));
        if (invalid) {
            StpUtil.logout(loginId);
            throw new NotLoginException(NotLoginException.INVALID_TOKEN, StpUtil.getLoginType(), "账号已停用或已过期，请重新联系管理员");
        }
    }
}
