package com.aurora.framework.mq;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;

/**
 * 对话模型全局并发闸。
 * 单实例内所有对话模型共用一个公平信号量，等待超过提醒阈值时通知调用方，超过总等待时长则放弃。
 */
@Slf4j
@Component
public class ApiRateLimiter {

    private static final int DEFAULT_MAX_CONCURRENCY = 50;
    private static final long DEFAULT_WARNING_SECONDS = 300;
    private static final long DEFAULT_TIMEOUT_SECONDS = 3000;

    private final Semaphore limiter;
    private final AtomicInteger waitingCount = new AtomicInteger();
    private final int maxConcurrency;
    private final long warningSeconds;
    private final long timeoutSeconds;

    /** 供独立测试运行器使用；Spring 运行时使用带配置参数的构造器。 */
    public ApiRateLimiter() {
        this(DEFAULT_MAX_CONCURRENCY, DEFAULT_WARNING_SECONDS, DEFAULT_TIMEOUT_SECONDS);
    }

    @Autowired
    public ApiRateLimiter(@Value("${ai.chat.concurrency.max:50}") int maxConcurrency,
                          @Value("${ai.chat.concurrency.wait-warning-seconds:300}") long warningSeconds,
                          @Value("${ai.chat.concurrency.wait-timeout-seconds:3000}") long timeoutSeconds) {
        this.maxConcurrency = Math.max(1, maxConcurrency);
        this.warningSeconds = Math.max(0, warningSeconds);
        this.timeoutSeconds = Math.max(this.warningSeconds, timeoutSeconds);
        this.limiter = new Semaphore(this.maxConcurrency, true);
        log.info("对话全局并发闸已创建: max={}, warning={}s, timeout={}s",
                this.maxConcurrency, this.warningSeconds, this.timeoutSeconds);
    }

    /**
     * 尝试获取许可（非阻塞）
     * 
     * @return true 如果成功获取，false 如果需要等待
     */
    public boolean tryAcquire(String apiProvider) {
        return limiter.tryAcquire();
    }

    /**
     * 获取许可（阻塞等待）
     */
    public void acquire(String apiProvider) throws InterruptedException {
        if (limiter.availablePermits() == 0) {
            int waitingNum = waitingCount.incrementAndGet();
            log.info("对话并发等待: 排队位置={}", waitingNum);
            try {
                limiter.acquire();
            } finally {
                waitingCount.decrementAndGet();
            }
        } else {
            limiter.acquire();
        }
    }

    /**
     * 按配置等待对话并发许可。
     *
     * @param warningCallback 超过提醒阈值仍未获得许可时回调一次，参数为当时等待数；可为空
     * @return true=已获得许可，false=超过总等待时长
     */
    public boolean acquireWithWaitNotice(IntConsumer warningCallback) throws InterruptedException {
        if (limiter.tryAcquire()) {
            return true;
        }

        int position = waitingCount.incrementAndGet();
        log.info("对话并发等待: 排队位置={}", position);
        try {
            if (warningSeconds > 0 && limiter.tryAcquire(warningSeconds, TimeUnit.SECONDS)) {
                return true;
            }
            if (warningCallback != null) {
                warningCallback.accept(waitingCount.get());
            }
            long remainingSeconds = timeoutSeconds - warningSeconds;
            return remainingSeconds > 0 && limiter.tryAcquire(remainingSeconds, TimeUnit.SECONDS);
        } finally {
            waitingCount.decrementAndGet();
        }
    }

    /**
     * 释放许可
     */
    public void release(String apiProvider) {
        limiter.release();
    }

    /**
     * 获取可用许可数
     */
    public int getAvailablePermits(String apiProvider) {
        return limiter.availablePermits();
    }

    /**
     * 获取等待队列长度
     */
    public int getQueueLength(String apiProvider) {
        return waitingCount.get();
    }

    /**
     * 获取最大并发数
     */
    public int getMaxConcurrency(String apiProvider) {
        return maxConcurrency;
    }

    /**
     * 获取所有供应商的限流状态
     */
    public Map<String, Map<String, Object>> getAllStatus() {
        int available = limiter.availablePermits();
        return Map.of("chat", Map.of(
                "available", available,
                "max", maxConcurrency,
                "waiting", waitingCount.get(),
                "inUse", maxConcurrency - available));
    }
}
