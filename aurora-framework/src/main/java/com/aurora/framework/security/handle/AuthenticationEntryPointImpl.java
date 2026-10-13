package com.aurora.framework.security.handle;

import com.aurora.common.core.domain.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 认证失败处理类 返回未授权
 */
@Component
public class AuthenticationEntryPointImpl implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException)
            throws IOException {
        // 检查是否是 token 相关的问题（可能是服务重启导致 token 缓存丢失）
        String errorMsg = "认证失败，无法访问系统资源";
        String requestURI = request.getRequestURI();

        // 如果是获取路由的请求，给出更明确的提示
        if (requestURI != null && (requestURI.contains("/getRouters") || requestURI.contains("/getInfo"))) {
            errorMsg = "登录已过期或服务已重启，请重新登录";
        } else {
            errorMsg = "请求访问：" + requestURI + "，认证失败，无法访问系统资源。请重新登录";
        }

        Result<Void> result = Result.error(401, errorMsg);
        String json = new ObjectMapper().writeValueAsString(result);
        response.setStatus(401);
        response.setContentType("application/json");
        response.setCharacterEncoding("utf-8");
        response.getWriter().print(json);
    }
}
