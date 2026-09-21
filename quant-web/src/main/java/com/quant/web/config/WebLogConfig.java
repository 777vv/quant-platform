package com.quant.web.config;

import com.quant.common.log.InvokeLogInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 日志配置（V1.9 ㊿）：注册接口调用日志拦截器，覆盖全部 /api 接口。
 * 拦截器先于鉴权拦截器执行顺序无强约束——无论登录与否都会记录（未登录请求同样留痕便于排查）。
 */
@Configuration
public class WebLogConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new InvokeLogInterceptor())
                .addPathPatterns("/api/**");
    }
}
