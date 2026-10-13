package com.aurora.framework.filter;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 链路追踪过滤器:为每个请求生成 / 透传 traceId,放入 MDC 供日志输出,并回写响应头 X-Trace-Id。
 *
 * <p>置于过滤器链最前(最高优先级),确保后续所有日志(含鉴权 / 业务 / 异常)都带同一 traceId。
 * <p>注意:虚拟线程下同请求线程内 MDC 正常;异步任务(MQ / 裸线程)不自动传播,本过滤器只覆盖 Web 请求链路。
 *
 * @author aurora
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter implements Filter {

    private static final String TRACE_ID = "traceId";
    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        // 上游已带 traceId 则透传,便于跨服务串联;否则新生成
        String traceId = null;
        if (request instanceof HttpServletRequest httpRequest) {
            traceId = httpRequest.getHeader(TRACE_ID_HEADER);
        }
        if (StrUtil.isBlank(traceId)) {
            traceId = IdUtil.fastSimpleUUID();
        }
        MDC.put(TRACE_ID, traceId);
        try {
            if (response instanceof HttpServletResponse httpResponse) {
                httpResponse.setHeader(TRACE_ID_HEADER, traceId);
            }
            chain.doFilter(request, response);
        } finally {
            // 务必清理,防止线程复用导致 traceId 串号
            MDC.remove(TRACE_ID);
        }
    }
}
