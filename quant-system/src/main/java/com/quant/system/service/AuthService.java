package com.quant.system.service;

import com.quant.system.dto.LoginRequest;
import com.quant.system.dto.LoginResult;
import com.quant.system.dto.PasswordRequest;
import com.quant.system.dto.UserVO;

/**
 * 认证服务：登录/登出/当前用户/修改密码
 */
public interface AuthService {

    LoginResult login(LoginRequest request);

    void logout();

    UserVO currentUser();

    void changePassword(PasswordRequest request);
}
