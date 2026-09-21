package com.quant.system.init;

import cn.dev33.satoken.secure.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.quant.system.entity.SysUser;
import com.quant.system.mapper.SysUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 管理员初始化：首次启动（用户表为空）时创建默认管理员，
 * 账号/密码来自配置 quant.init.admin-username / admin-password（默认 admin/admin123，登录后请立即修改）
 */
@Component
public class AdminUserInitializer implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminUserInitializer.class);

    private final SysUserMapper sysUserMapper;

    @Value("${quant.init.admin-username:admin}")
    private String adminUsername;

    @Value("${quant.init.admin-password:admin123}")
    private String adminPassword;

    public AdminUserInitializer(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        Long count = sysUserMapper.selectCount(new LambdaQueryWrapper<>());
        if (count != null && count > 0) {
            return;
        }
        SysUser user = new SysUser();
        user.setUsername(adminUsername);
        user.setPassword(BCrypt.hashpw(adminPassword, BCrypt.gensalt()));
        user.setNickname("管理员");
        user.setFailCount(0);
        sysUserMapper.insert(user);
        LOGGER.warn("已初始化默认管理员[{}]，默认密码来自配置项，登录后请立即修改密码", adminUsername);
    }
}
