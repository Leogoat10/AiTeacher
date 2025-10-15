// java
package com.leo.aiteacher.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 认证拦截器：兼容 teacher 和 student 两类 session
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 放行预检请求
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String uri = request.getRequestURI();
        if (uri.startsWith("/login") || uri.startsWith("/api/login") || uri.startsWith("/swagger") || uri.startsWith("/v3/api-docs")) {
            return true;
        }

        HttpSession session = request.getSession(false);
        if (session == null) {
            unauthorized(response);
            return false;
        }

        Object teacher = session.getAttribute("teacher");
        Object student = session.getAttribute("student");

        if (teacher == null && student == null) {
            unauthorized(response);
            return false;
        }

        // 至此表示已登录（教师或学生），放行
        return true;
    }

    private void unauthorized(HttpServletResponse response) throws java.io.IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"Unauthorized\"}");
    }
}