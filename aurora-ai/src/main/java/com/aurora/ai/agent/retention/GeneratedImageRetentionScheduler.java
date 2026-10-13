package com.aurora.ai.agent.retention;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 每天按配置时间执行生成图片保留期清理。 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai.generated-image.cleanup", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GeneratedImageRetentionScheduler {

    private final GeneratedImageRetentionService retentionService;

    @Scheduled(
            cron = "${ai.generated-image.cleanup.cron:0 20 3 * * *}",
            zone = "${ai.generated-image.cleanup.zone:Asia/Shanghai}")
    public void cleanup() {
        try {
            retentionService.cleanupExpired();
        } catch (Exception e) {
            log.error("生成图片保留期定时清理失败", e);
        }
    }
}
