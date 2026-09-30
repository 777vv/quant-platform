package com.quant.system.service;

import com.quant.common.result.PageResult;
import com.quant.system.dto.LoginLogVO;

/**
 * 登录日志服务（V5.46）：记录每次登录尝试并按条件分页查询。
 */
public interface LoginLogService {

    /**
     * 记录一次登录尝试（成功与失败都记）。
     *
     * <p>从当前请求上下文自动取 IP / UA / traceId；**任何异常都在内部吞掉并记 error 日志**——
     * 登录日志是审计辅助，写不进去绝不能影响登录本身（与铁律 13 的"catch 必记 error+堆栈"一致）。
     *
     * @param username   登录用户名（用户不存在时也记，便于识别撞库/输错）
     * @param success    是否登录成功
     * @param failReason 失败原因（成功时传 null）
     */
    void record(String username, boolean success, String failReason);

    /**
     * 分页查询登录日志（按时间倒序）。
     *
     * @param username 用户名关键字（模糊匹配，可空）
     * @param success  结果筛选（1=只看成功 0=只看失败，null=全部）
     * @param page     页码（1 起）
     * @param size     每页条数
     */
    PageResult<LoginLogVO> page(String username, Integer success, long page, long size);
}
