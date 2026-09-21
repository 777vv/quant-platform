package com.quant.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.quant.common.result.R;
import com.quant.common.result.ResultCodeEnum;

/**
 * 全局异常处理器：所有异常统一转换为 R 响应，日志自动携带 traceId
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常：预期内失败，WARN 记录并透出原因 */
    @ExceptionHandler(BizException.class)
    public R<Void> handleBizException(BizException e) {
        LOGGER.warn("业务异常: {}", e.getMessage());
        return R.fail(e.getCode(), e.getMessage());
    }

    /** 参数校验失败：返回第一条字段校验消息 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> handleValidException(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError == null ? ResultCodeEnum.PARAM_ERROR.getMessage() : fieldError.getDefaultMessage();
        LOGGER.warn("参数校验失败: {}", message);
        return R.fail(ResultCodeEnum.PARAM_ERROR.getCode(), message);
    }

    /**
     * 单参数校验失败（@RequestParam / @PathVariable 上的注解，如 check 接口的代码长度）：
     * 走 ConstraintViolationException，此前落入兜底 handler 返回"系统异常"，用户看不到具体原因。
     */
    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public R<Void> handleConstraintViolation(jakarta.validation.ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .findFirst()
                .map(violation -> violation.getMessage())
                .orElse(ResultCodeEnum.PARAM_ERROR.getMessage());
        LOGGER.warn("参数校验失败: {}", message);
        return R.fail(ResultCodeEnum.PARAM_ERROR.getCode(), message);
    }

    /** 静态资源/接口路径不存在 */
    @ExceptionHandler(NoResourceFoundException.class)
    public R<Void> handleNoResourceFound(NoResourceFoundException e) {
        return R.fail(ResultCodeEnum.NOT_FOUND);
    }

    /** 兜底：未预期异常，ERROR 记录完整堆栈，对前端隐藏细节 */
    @ExceptionHandler(Exception.class)
    public R<Void> handleException(Exception e) {
        LOGGER.error("系统异常", e);
        return R.fail(ResultCodeEnum.ERROR);
    }
}
