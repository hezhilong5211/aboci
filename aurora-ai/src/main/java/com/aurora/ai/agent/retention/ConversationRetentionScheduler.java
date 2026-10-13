package com.aurora.ai.agent.retention;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 每天清理连续一个保留期未使用的对话及消息。 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai.conversation.cleanup", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ConversationRetentionScheduler {

    private final ConversationRetentionService retentionService;

    @Scheduled(
            cron = "${ai.conversation.cleanup.cron:0 40 3 * * *}",
            zone = "${ai.conversation.cleanup.zone:Asia/Shanghai}")
    public void cleanup() {
        try {
            retentionService.cleanupExpired();
        } catch (Exception e) {
            log.error("对话保留期定时清理失败", e);
        }
    }
}
