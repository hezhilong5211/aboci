package com.aurora.framework.mobile;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * 移动端 JWT 过滤器
 * 从 Authorization Bearer 取 token 校验,成功则注入 mobileUserId 请求属性并设置 SecurityContext 认证;
 * 无 token 或校验失败均放行(由 SecurityFilterChain 的 authenticated 决定是否拦截),不中断过滤链。
 */
@Component
@RequiredArgsConstructor
public class MobileJwtFilter extends OncePerRequestFilter {

    /** 当前移动端用户ID 请求属性名 */
    public static final String MOBILE_USER_ID_ATTR = "mobileUserId";

    private final MobileTokenService mobileTokenService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = resolveToken(request);
        if (StringUtils.hasText(token)) {
            try {
                Long userId = mobileTokenService.getUserId(token);
                request.setAttribute(MOBILE_USER_ID_ATTR, userId);
                // 设置认证以通过 anyRequest().authenticated();无角色权限,principal 用 userId
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception e) {
                // 校验失败不中断链,交由 SecurityFilterChain 决定放行或拦截
            }
        }
        chain.doFilter(request, response);
    }

    /**
     * 从 Authorization 头取 Bearer 令牌
     */
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
