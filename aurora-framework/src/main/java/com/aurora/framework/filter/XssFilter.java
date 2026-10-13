package com.aurora.framework.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;

/**
 * XSS 过滤器
 * 对请求参数进行 HTML 转义，防止 XSS 攻击
 *
 * @author aurora
 */
@Slf4j
@Component
public class XssFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String uri = httpRequest.getRequestURI();

        // 排除 API 文档和静态资源（不需要 XSS 过滤）
        if (uri.contains("/v3/api-docs") || uri.contains("/swagger") ||
                uri.contains("/doc.html") || uri.contains("/webjars")) {
            chain.doFilter(request, response);
            return;
        }

        // 排除文件上传等不需要过滤的请求
        String contentType = httpRequest.getContentType();
        if (contentType != null && contentType.contains("multipart/form-data")) {
            chain.doFilter(request, response);
            return;
        }

        // JSON 请求体过滤
        if (contentType != null && contentType.contains("application/json")) {
            try {
                chain.doFilter(new XssJsonRequestWrapper(httpRequest), response);
            } catch (Exception e) {
                log.warn("XSS JSON 过滤失败: {}", e.getMessage());
                chain.doFilter(request, response); // Fallback to original request on error
            }
            return;
        }

        // 普通参数过滤
        chain.doFilter(new XssHttpServletRequestWrapper(httpRequest), response);
    }

    /**
     * XSS 请求包装器
     */
    private static class XssHttpServletRequestWrapper extends HttpServletRequestWrapper {

        public XssHttpServletRequestWrapper(HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getParameter(String name) {
            String value = super.getParameter(name);
            return cleanXss(value);
        }

        @Override
        public String[] getParameterValues(String name) {
            String[] values = super.getParameterValues(name);
            if (values == null) {
                return null;
            }
            String[] cleanValues = new String[values.length];
            for (int i = 0; i < values.length; i++) {
                cleanValues[i] = cleanXss(values[i]);
            }
            return cleanValues;
        }

        @Override
        public String getHeader(String name) {
            String value = super.getHeader(name);
            return cleanXss(value);
        }

        /**
         * 清理 XSS 攻击代码
         */
        private String cleanXss(String value) {
            if (value == null || value.isEmpty()) {
                return value;
            }
            // HTML 转义
            return HtmlUtils.htmlEscape(value);
        }
    }
}
