package com.aurora.framework.mobile;

import com.aurora.common.core.domain.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 移动端认证失败入口。
 *
 * <p>返回 HTTP 200 + 业务码 401,对齐 hagent(PHP 风格)请求层"业务 code===401 触发刷新令牌"的契约
 * (与管理后台 {@code AuthenticationEntryPointImpl} 返回 HTTP 401 的策略区分,两条链各自独立)。
 * token 缺失或失效时由此返回,前端据 code 401 自动走刷新/重登录流程。</p>
 */
@Component
public class MobileAuthEntryPoint implements AuthenticationEntryPoint {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        Result<Void> result = Result.error(401, "登录已过期，请重新登录");
        // 故意用 HTTP 200 承载业务码 401,迎合移动端请求层契约(前端无需改动)
        response.setStatus(200);
        response.setContentType("application/json");
        response.setCharacterEncoding("utf-8");
        response.getWriter().print(MAPPER.writeValueAsString(result));
    }
}
