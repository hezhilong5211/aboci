package com.aurora.framework.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 熔断器
 * 当外部服务异常时自动熔断，防止雪崩
 *
 * @author aurora
 */
@Slf4j
@Component
public class CircuitBreaker {

    /**
     * 熔断器状态
     */
    public enum State {
        CLOSED, // 正常，允许请求
        OPEN, // 熔断，拒绝请求
        HALF_OPEN // 半开，允许少量请求探测
    }

    /**
     * 熔断器配置
     */
    private static class CircuitConfig {
        AtomicInteger failureCount = new AtomicInteger(0);
        State state = State.CLOSED;
        long lastFailureTime = 0;
        long openTime = 0;
    }

    private final Map<String, CircuitConfig> circuits = new ConcurrentHashMap<>();

    /**
     * 失败阈值（连续失败N次后熔断）
     */
    private static final int FAILURE_THRESHOLD = 5;

    /**
     * 熔断恢复时间（毫秒）
     */
    private static final long RECOVERY_TIME_MS = 30_000; // 30秒

    /**
     * 失败计数重置时间（毫秒）
     */
    private static final long FAILURE_RESET_TIME_MS = 60_000; // 60秒

    /**
     * 检查是否允许请求
     *
     * @param serviceName 服务名称
     * @return true=允许，false=熔断中
     */
    public boolean allowRequest(String serviceName) {
        CircuitConfig config = circuits.computeIfAbsent(serviceName, k -> new CircuitConfig());

        switch (config.state) {
            case CLOSED:
                return true;
            case OPEN:
                // 检查是否可以进入半开状态
                if (System.currentTimeMillis() - config.openTime > RECOVERY_TIME_MS) {
                    config.state = State.HALF_OPEN;
                    log.info("熔断器 {} 进入半开状态", serviceName);
                    return true;
                }
                return false;
            case HALF_OPEN:
                // 半开状态只允许一个请求探测
                return true;
            default:
                return true;
        }
    }

    /**
     * 记录请求成功
     */
    public void recordSuccess(String serviceName) {
        CircuitConfig config = circuits.get(serviceName);
        if (config == null) {
            return;
        }

        if (config.state == State.HALF_OPEN) {
            // 半开状态成功，恢复正常
            config.state = State.CLOSED;
            config.failureCount.set(0);
            log.info("熔断器 {} 恢复正常", serviceName);
        } else if (config.state == State.CLOSED) {
            // 正常状态成功，重置失败计数
            config.failureCount.set(0);
        }
    }

    /**
     * 记录请求失败
     */
    public void recordFailure(String serviceName) {
        CircuitConfig config = circuits.computeIfAbsent(serviceName, k -> new CircuitConfig());

        // 如果距离上次失败超过重置时间，重置计数
        if (System.currentTimeMillis() - config.lastFailureTime > FAILURE_RESET_TIME_MS) {
            config.failureCount.set(0);
        }

        config.lastFailureTime = System.currentTimeMillis();
        int failures = config.failureCount.incrementAndGet();

        if (config.state == State.HALF_OPEN) {
            // 半开状态失败，重新熔断
            config.state = State.OPEN;
            config.openTime = System.currentTimeMillis();
            log.warn("熔断器 {} 半开探测失败，重新熔断", serviceName);
        } else if (config.state == State.CLOSED && failures >= FAILURE_THRESHOLD) {
            // 正常状态失败达到阈值，熔断
            config.state = State.OPEN;
            config.openTime = System.currentTimeMillis();
            log.warn("熔断器 {} 触发熔断，连续失败 {} 次", serviceName, failures);
        }
    }

    /**
     * 获取熔断器状态
     */
    public State getState(String serviceName) {
        CircuitConfig config = circuits.get(serviceName);
        return config != null ? config.state : State.CLOSED;
    }

    /**
     * 手动重置熔断器
     */
    public void reset(String serviceName) {
        circuits.remove(serviceName);
        log.info("熔断器 {} 已手动重置", serviceName);
    }

    /**
     * 获取所有熔断器状态（用于监控）
     */
    public Map<String, String> getAllStates() {
        Map<String, String> states = new ConcurrentHashMap<>();
        circuits.forEach((name, config) -> states.put(name, config.state.name()));
        return states;
    }
}
