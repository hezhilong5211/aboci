package com.aurora.framework.mq;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aurora.common.core.domain.Result;

import java.util.Map;

/**
 * API 限流状态监控接口
 */
@RestController
@RequestMapping("/api/rate-limit")
@RequiredArgsConstructor
public class RateLimitStatusController {

    private final ApiRateLimiter apiRateLimiter;

    /**
     * 获取所有 API 供应商的限流状态
     */
    @GetMapping("/status")
    public Result<Map<String, Map<String, Object>>> getStatus() {
        return Result.success(apiRateLimiter.getAllStatus());
    }
}
