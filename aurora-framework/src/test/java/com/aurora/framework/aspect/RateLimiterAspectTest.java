package com.aurora.framework.aspect;

import com.aurora.common.core.exception.ServiceException;
import com.aurora.common.core.redis.RedisCache;
import com.aurora.framework.annotation.RateLimiter;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RateLimiterAspectTest {

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void shouldReturnStableBusinessCodeWhenLimitExceeded() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        RateLimiterAspect aspect = new RateLimiterAspect(new CountingRedisCache());
        Method method = TestEndpoint.class.getDeclaredMethod("limited");
        RateLimiter rateLimiter = method.getAnnotation(RateLimiter.class);
        ProceedingJoinPoint point = successfulPoint();

        assertEquals("ok", aspect.around(point, rateLimiter));
        ServiceException exception = assertThrows(ServiceException.class,
                () -> aspect.around(point, rateLimiter));

        assertEquals(429, exception.getCode());
        assertEquals("请求过于频繁", exception.getMessage());
    }

    private ProceedingJoinPoint successfulPoint() {
        return (ProceedingJoinPoint) Proxy.newProxyInstance(
                ProceedingJoinPoint.class.getClassLoader(),
                new Class<?>[]{ProceedingJoinPoint.class},
                (proxy, method, args) -> "proceed".equals(method.getName()) ? "ok" : null);
    }

    private static class CountingRedisCache extends RedisCache {
        private final AtomicLong count = new AtomicLong();

        @Override
        public Long increment(String key) {
            return count.incrementAndGet();
        }

        @Override
        public boolean expire(String key, long timeout) {
            return true;
        }
    }

    private static class TestEndpoint {
        @RateLimiter(key = "test", count = 1, message = "请求过于频繁")
        void limited() {
        }
    }
}
