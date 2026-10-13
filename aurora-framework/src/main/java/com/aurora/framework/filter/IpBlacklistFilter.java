package com.aurora.framework.filter;

import com.aurora.framework.security.IpBlacklistManager;
import com.aurora.common.core.utils.IpUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * IP 黑名单过滤器
 * 在请求进入应用前检查IP是否被封锁
 * 优先级最高，最先执行
 *
 * @author aurora
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class IpBlacklistFilter implements Filter {

    @Autowired(required = false)
    private IpBlacklistManager blacklistManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (blacklistManager == null) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String ip = IpUtils.getIpAddr(httpRequest);

        // 检查IP是否被封锁
        if (blacklistManager.isBlocked(ip)) {
            log.debug("IP {} 已被封锁，拒绝访问", ip);

            HttpServletResponse httpResponse = (HttpServletResponse) response;
            httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
            httpResponse.setContentType("application/json;charset=UTF-8");

            Map<String, Object> result = new HashMap<>();
            result.put("code", 403);
            result.put("msg", "您的IP已被临时封锁，请30分钟后再试");
            result.put("data", null);

            httpResponse.getWriter().write(objectMapper.writeValueAsString(result));
            return;
        }

        chain.doFilter(request, response);
    }
}
