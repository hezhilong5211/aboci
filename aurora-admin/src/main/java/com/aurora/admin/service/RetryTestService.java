package com.aurora.admin.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 重试测试服务
 * 必须是独立的 Service 类，@Retryable 才能通过 AOP 代理生效
 */
@Slf4j
@Service
public class RetryTestService {

    // 记录重试次数
    private final AtomicInteger retryCounter = new AtomicInteger(0);

    /**
     * 重置计数器
     */
    public void resetCounter() {
        retryCounter.set(0);
    }

    /**
     * 获取当前尝试次数
     */
    public int getAttemptCount() {
        return retryCounter.get();
    }

    /**
     * 带重试的方法
     * 模拟前 N 次失败，之后成功
     * 
     * @param failTimes 需要失败的次数
     * @return 成功消息
     */
    @Retryable(retryFor = {
            RuntimeException.class }, maxAttempts = 3, backoff = @Backoff(delay = 500, multiplier = 2, maxDelay = 5000))
    public String doRetryableWork(int failTimes) {
        int attempt = retryCounter.incrementAndGet();
        log.info("🔄 第 {} 次尝试（预期失败 {} 次）", attempt, failTimes);

        if (attempt <= failTimes) {
            log.warn("❌ 第 {} 次尝试失败，将重试...", attempt);
            throw new RuntimeException("模拟失败，第 " + attempt + " 次尝试");
        }

        log.info("✅ 第 {} 次尝试成功！", attempt);
        return "成功！总共尝试了 " + attempt + " 次";
    }

    /**
     * 降级处理方法
     * 当所有重试都失败后调用
     * 方法签名必须匹配：返回类型相同，第一个参数是异常，后续参数与原方法相同
     */
    @Recover
    public String recoverFromFailure(RuntimeException e, int failTimes) {
        int attempts = retryCounter.get();
        log.error("🔴 所有 {} 次重试都失败，执行降级处理", attempts);
        return "降级结果：已达到最大重试次数 (" + attempts + " 次)，错误: " + e.getMessage();
    }
}
