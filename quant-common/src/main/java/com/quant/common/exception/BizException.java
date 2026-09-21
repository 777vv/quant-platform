package com.quant.common.exception;

import com.quant.common.result.ResultCodeEnum;

/**
 * 业务异常：业务代码中主动抛出，由全局异常处理器统一转换为 R 响应
 */
public class BizException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int code;

    public BizException(String message) {
        super(message);
        this.code = ResultCodeEnum.ERROR.getCode();
    }

    public BizException(ResultCodeEnum resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public BizException(ResultCodeEnum resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }

    public int getCode() {
        return code;
    }
}
