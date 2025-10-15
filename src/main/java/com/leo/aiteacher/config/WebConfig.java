// src/main/java/com/leo/aiteacher/config/WebConfig.java
package com.leo.aiteacher.config;

import com.leo.aiteacher.interceptor.AuthInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private AuthInterceptor authInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册自定义的认证拦截器 AuthInterceptor
        registry.addInterceptor(authInterceptor)
                // 对所有路径应用该拦截器
                .addPathPatterns("/**")
                // 排除不需要认证的路径
                .excludePathPatterns(
                        "/login/**",        // 登录相关接口
                        "/error",           // 错误处理页面
                        "/static/**",       // 静态资源文件
                        "/swagger-ui/**",   // Swagger UI 页面
                        "/v3/api-docs/**"   // Swagger API 文档接口
                );
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 配置跨域资源共享(CORS)策略
        // 允许跨域并携带 cookie（前端需设置 withCredentials: true）
        registry.addMapping("/**")
                .allowedOriginPatterns("*")    // 允许所有来源域名
                .allowedMethods("*")           // 允许所有HTTP方法(GET, POST, PUT, DELETE等)
                .allowedHeaders("*")           // 允许所有请求头
                .allowCredentials(true)        // 允许携带认证信息(cookie等)
                .maxAge(3600);                 // 预检请求缓存时间(秒)
    }
}
