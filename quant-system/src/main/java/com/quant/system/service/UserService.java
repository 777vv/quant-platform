package com.quant.system.service;

import com.quant.system.dto.ProfileRequest;
import com.quant.system.dto.UserVO;

/**
 * 用户资料服务
 */
public interface UserService {

    UserVO profile();

    void updateProfile(ProfileRequest request);
}
