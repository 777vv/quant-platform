package com.quant.system.config;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.quant.common.result.R;
import com.quant.common.result.ResultCodeEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * SaToken 异常转译：未登录 → 401（前端据此跳转登录页）；无权限/角色 → 403。
 * 优先级高于 quant-common 的全局兜底处理器。
 */
@RestControllerAdvice
@Order(-1)
public class SaTokenExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SaTokenExceptionHandler.class);

    @ExceptionHandler(NotLoginException.class)
    public R<Void> handleNotLogin(NotLoginException e) {
        LOGGER.error("未登录访问: {}", e.getMessage());
        return R.fail(ResultCodeEnum.UNAUTHORIZED);
    }

    @ExceptionHandler({NotPermissionException.class, NotRoleException.class})
    public R<Void> handleNoPermission(Exception e) {
        LOGGER.warn("权限不足: {}", e.getMessage());
        return R.fail(ResultCodeEnum.FORBIDDEN);
    }
}
