package com.aurora.admin.mobile.retention;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 每天在生成结果与对话清理完成后，清理无引用的过期上传参考图。 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai.uploaded-reference.cleanup", name = "enabled", havingValue = "true", matchIfMissing = true)
public class UploadedReferenceRetentionScheduler {

    private final UploadedReferenceRetentionService retentionService;

    @Scheduled(
            cron = "${ai.uploaded-reference.cleanup.cron:0 0 4 * * *}",
            zone = "${ai.uploaded-reference.cleanup.zone:Asia/Shanghai}")
    public void cleanup() {
        try {
            retentionService.cleanupExpired();
        } catch (Exception e) {
            log.error("上传参考图保留期定时清理失败", e);
        }
    }
}
