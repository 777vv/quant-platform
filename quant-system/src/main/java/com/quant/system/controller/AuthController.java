package com.quant.system.controller;

import com.quant.common.result.R;
import com.quant.system.dto.LoginRequest;
import com.quant.system.dto.LoginResult;
import com.quant.system.dto.PasswordRequest;
import com.quant.system.dto.UserVO;
import com.quant.system.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口（FR4）：登录/登出/当前用户/修改密码
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** 登录（免鉴权），成功返回 token 与用户信息 */
    @PostMapping("/login")
    public R<LoginResult> login(@Valid @RequestBody LoginRequest request) {
        return R.ok(authService.login(request));
    }

    /** 登出（销毁当前会话） */
    @PostMapping("/logout")
    public R<Void> logout() {
        authService.logout();
        return R.ok();
    }

    /** 当前登录用户信息 */
    @GetMapping("/me")
    public R<UserVO> me() {
        return R.ok(authService.currentUser());
    }

    /** 修改密码：校验原密码，成功后全端下线强制重新登录 */
    @PutMapping("/password")
    public R<Void> changePassword(@Valid @RequestBody PasswordRequest request) {
        authService.changePassword(request);
        return R.ok();
    }
}
