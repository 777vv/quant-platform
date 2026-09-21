package com.quant.common.result;

/**
 * 统一响应码枚举
 */
public enum ResultCodeEnum {

    /** 成功 */
    SUCCESS(0, "成功"),

    /** 参数错误 */
    PARAM_ERROR(400, "参数错误"),

    /** 未登录/会话失效 */
    UNAUTHORIZED(401, "未登录或会话已失效"),

    /** 无权限 */
    FORBIDDEN(403, "无权限"),

    /** 资源不存在 */
    NOT_FOUND(404, "资源不存在"),

    /** 系统异常 */
    ERROR(500, "系统异常，请稍后重试");

    private final int code;

    private final String message;

    ResultCodeEnum(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
