package com.aurora.framework.aspect;

import com.aurora.common.core.constant.CacheConstants;
import com.aurora.common.core.exception.ServiceException;
import com.aurora.common.core.redis.RedisCache;
import com.aurora.common.core.utils.IpUtils;
import com.aurora.framework.annotation.RateLimiter;
import com.aurora.framework.security.IpBlacklistManager;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * 限流 AOP 切面
 *
 * @author aurora
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimiterAspect {

    private final RedisCache redisCache;

    @Autowired(required = false)
    private IpBlacklistManager blacklistManager;

    private static final String RATE_LIMIT_KEY_PREFIX = CacheConstants.RATE_LIMIT_KEY;

    @Around("@annotation(rateLimiter)")
    public Object around(ProceedingJoinPoint point, RateLimiter rateLimiter) throws Throwable {
        String key = buildKey(point, rateLimiter);
        int time = rateLimiter.time();
        int count = rateLimiter.count();

        // 原子自增计数,仅在首次创建 key 时设置 TTL(避免读-改-写竞态被并发击穿)
        Long currentCount = redisCache.increment(key);
        if (currentCount != null && currentCount == 1L) {
            redisCache.expire(key, time);
        }

        if (currentCount != null && currentCount > count) {
            // 超过限制，记录触发并检查是否需要封锁
            String ip = getClientIp();
            log.warn("接口限流触发: key={}, count={}/{}, ip={}", key, currentCount, count, ip);

            // 记录限流触发，检查是否需要封锁IP
            if (blacklistManager != null) {
                blacklistManager.recordTriggerAndCheck(ip);
            }

            throw new ServiceException(rateLimiter.message(), 429);
        }

        return point.proceed();
    }

    /**
     * 构建限流 key
     */
    private String buildKey(ProceedingJoinPoint point, RateLimiter rateLimiter) {
        StringBuilder sb = new StringBuilder(RATE_LIMIT_KEY_PREFIX);

        // 添加自定义 key 前缀
        if (!rateLimiter.key().isEmpty()) {
            sb.append(rateLimiter.key()).append(":");
        } else {
            // 使用方法签名作为 key
            MethodSignature signature = (MethodSignature) point.getSignature();
            Method method = signature.getMethod();
            sb.append(method.getDeclaringClass().getSimpleName())
                    .append(".")
                    .append(method.getName())
                    .append(":");
        }

        // 根据限流类型添加标识
        if (rateLimiter.limitType() == RateLimiter.LimitType.IP) {
            sb.append(getClientIp());
        } else {
            // 按用户限流
            try {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                    sb.append("user:").append(auth.getName());
                } else {
                    sb.append(getClientIp());
                }
            } catch (Exception e) {
                sb.append(getClientIp());
            }
        }

        return sb.toString();
    }

    /**
     * 获取客户端 IP
     */
    private String getClientIp() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "unknown";
        }
        HttpServletRequest request = attributes.getRequest();
        return IpUtils.getIpAddr(request);
    }
}
