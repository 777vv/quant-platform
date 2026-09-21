package com.quant.common.result;

import java.io.Serializable;

import org.slf4j.MDC;

/**
 * 统一响应体：code=0 成功；非 0 为业务错误码；traceId 贯穿本次请求的全部日志（技术文档 14.2）
 */
public class R<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 业务码：0=成功，401=未登录，500=系统异常等，见 ResultCodeEnum */
    private int code;

    /** 提示信息（成功="成功"，失败=可直接展示的原因） */
    private String message;

    /** 业务数据载荷 */
    private T data;

    /** 链路追踪 ID（构造时从 MDC 读取，前端报错时反馈此值定位日志） */
    private String traceId;

    private R(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = MDC.get("traceId");
    }

    /** 成功（无数据） */
    public static <T> R<T> ok() {
        return new R<>(ResultCodeEnum.SUCCESS.getCode(), ResultCodeEnum.SUCCESS.getMessage(), null);
    }

    /** 成功（携带数据） */
    public static <T> R<T> ok(T data) {
        return new R<>(ResultCodeEnum.SUCCESS.getCode(), ResultCodeEnum.SUCCESS.getMessage(), data);
    }

    /** 失败（自定义业务码与消息） */
    public static <T> R<T> fail(int code, String message) {
        return new R<>(code, message, null);
    }

    /** 失败（预置错误码） */
    public static <T> R<T> fail(ResultCodeEnum resultCode) {
        return new R<>(resultCode.getCode(), resultCode.getMessage(), null);
    }

    /** 失败（预置错误码 + 覆盖消息） */
    public static <T> R<T> fail(ResultCodeEnum resultCode, String message) {
        return new R<>(resultCode.getCode(), message, null);
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }

    public String getTraceId() {
        return traceId;
    }
}
