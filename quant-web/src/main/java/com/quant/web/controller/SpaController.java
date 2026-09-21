package com.quant.web.controller;

import com.quant.common.exception.BizException;
import com.quant.common.result.ResultCodeEnum;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * SPA 路由回退：非 /api、非静态资源的路径统一转发 index.html（前后端不分离，技术文档 8.2）
 */
@Controller
public class SpaController {

    @GetMapping({"/", "/{path:[^\\.]*}", "/{a:[^\\.]*}/{path:[^\\.]*}", "/{a:[^\\.]*}/{b:[^\\.]*}/{path:[^\\.]*}"})
    public String forward(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri.startsWith("/api/") || uri.startsWith("/actuator")) {
            throw new BizException(ResultCodeEnum.NOT_FOUND, "接口不存在");
        }
        return "forward:/index.html";
    }
}
