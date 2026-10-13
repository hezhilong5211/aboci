package com.aurora.ai.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

/**
 * 重试机制配置
 * 启用 Spring Retry，为 AI 调用等网络请求提供自动重试能力
 * 
 * @author aurora
 */
@Configuration
@EnableRetry
public class RetryConfig {
    // @EnableRetry 启用后，可在任意 Service 方法上使用 @Retryable 注解
    // 配置示例：
    // @Retryable(
    // value = {Exception.class}, // 需要重试的异常类型
    // maxAttempts = 3, // 最大重试次数（包括首次调用）
    // backoff = @Backoff(delay = 1000, multiplier = 2) // 指数退避
    // )
}
