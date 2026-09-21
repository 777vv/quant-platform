package com.quant.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.quant.common.spi.MailRecipientProvider;
import com.quant.system.entity.SysUser;
import com.quant.system.mapper.SysUserMapper;
import org.springframework.stereotype.Component;

/**
 * 通知收件人提供方实现（M4-04）：返回管理员资料中的通知邮箱。
 * 单用户系统取第一个账号；未维护邮箱返回 null，由通知模块决定降级行为。
 */
@Component
public class AdminMailRecipientProvider implements MailRecipientProvider {

    private final SysUserMapper sysUserMapper;

    public AdminMailRecipientProvider(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    public String notifyRecipient() {
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .orderByAsc(SysUser::getId).last("limit 1"));
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            return null;
        }
        return user.getEmail().trim();
    }
}
