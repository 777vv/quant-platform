package com.quant.system.controller;

import com.quant.common.result.PageResult;
import com.quant.common.result.R;
import com.quant.system.dto.LoginLogVO;
import com.quant.system.dto.LoginRequest;
import com.quant.system.dto.LoginResult;
import com.quant.system.dto.PasswordRequest;
import com.quant.system.dto.UserVO;
import com.quant.system.service.AuthService;
import com.quant.system.service.LoginLogService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.quant.common.auth.PermissionCodes;

/**
 * 认证接口（FR4）：登录/登出/当前用户/修改密码/登录日志查询
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    /** 登录日志查询（V5.46） */
    private final LoginLogService loginLogService;

    public AuthController(AuthService authService, LoginLogService loginLogService) {
        this.authService = authService;
        this.loginLogService = loginLogService;
    }

    /** 登录（免鉴权），成功返回 token 与用户信息 */
    @PostMapping("/login")
    public R<LoginResult> login(@Valid @RequestBody LoginRequest request) {
        return R.ok(authService.login(request));
    }

    /**
     * 登录日志分页查询（V5.46，按时间倒序）。
     *
     * @param username 用户名关键字（模糊，可空）
     * @param success  结果筛选：1=只看成功 0=只看失败，不传=全部
     */
    @SaCheckPermission(com.quant.common.auth.PermissionCodes.MENU_LOGIN_LOGS)
    @GetMapping("/login-logs")
    public R<PageResult<LoginLogVO>> loginLogs(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Integer success,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return R.ok(loginLogService.page(username, success, page, size));
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
