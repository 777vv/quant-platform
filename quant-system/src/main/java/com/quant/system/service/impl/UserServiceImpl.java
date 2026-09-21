package com.quant.system.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.quant.common.exception.BizException;
import com.quant.system.dto.ProfileRequest;
import com.quant.system.dto.UserVO;
import com.quant.system.entity.SysUser;
import com.quant.system.mapper.SysUserMapper;
import com.quant.system.service.UserService;
import org.springframework.stereotype.Service;

/**
 * 用户资料服务实现
 */
@Service
public class UserServiceImpl implements UserService {

    private final SysUserMapper sysUserMapper;

    public UserServiceImpl(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    public UserVO profile() {
        SysUser user = sysUserMapper.selectById(StpUtil.getLoginIdAsLong());
        if (user == null) {
            throw new BizException("用户不存在");
        }
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setEmail(user.getEmail());
        vo.setLastLoginAt(user.getLastLoginAt());
        return vo;
    }

    @Override
    public void updateProfile(ProfileRequest request) {
        sysUserMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, StpUtil.getLoginIdAsLong())
                .set(SysUser::getNickname, request.getNickname())
                .set(SysUser::getEmail, request.getEmail()));
    }
}
