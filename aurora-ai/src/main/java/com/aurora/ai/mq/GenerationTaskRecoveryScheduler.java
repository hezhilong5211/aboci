package com.aurora.ai.mq;

import com.aurora.ai.agent.domain.GenerationTask;
import com.aurora.ai.agent.service.IGenerationTaskService;
import com.aurora.framework.mq.TaskProducer;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.Instant;
import java.time.ZoneId;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Map;

/** Recovery for the existing single-backend deployment; unknown supplier results are never regenerated. */
@Component
@Slf4j
@RequiredArgsConstructor
public class GenerationTaskRecoveryScheduler {
    private final IGenerationTaskService tasks;
    private final TaskProducer queue;
    private final LocalDateTime startup = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(ManagementFactory.getRuntimeMXBean().getStartTime()), ZoneId.systemDefault());
    private volatile boolean ready;

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterrupted() {
        List<GenerationTask> interrupted = tasks.list(new LambdaQueryWrapper<GenerationTask>()
                .eq(GenerationTask::getStatus, "RUNNING").lt(GenerationTask::getUpdateTime, startup));
        for (GenerationTask task : interrupted) {
            int success = task.getSuccessCount() == null ? 0 : task.getSuccessCount();
            tasks.update(new LambdaUpdateWrapper<GenerationTask>()
                    .eq(GenerationTask::getId, task.getId()).eq(GenerationTask::getStatus, "RUNNING")
                    .lt(GenerationTask::getUpdateTime, startup)
                    .set(GenerationTask::getStatus, success > 0 ? "SUCCEEDED" : "FAILED")
                    .set(GenerationTask::getSuccessCount, success)
                    .set(GenerationTask::getUpdateTime, LocalDateTime.now())
                    .set(GenerationTask::getErrorMsg, "服务重启导致出图中断，未完成图片将退豆"));
        }
        ready = true;
        republishPending();
    }

    @Scheduled(fixedDelayString = "${ai.image.recovery-delay-ms:60000}")
    public void republishPending() {
        if (!ready) return;
        List<GenerationTask> pending = tasks.list(new LambdaQueryWrapper<GenerationTask>()
                .and(wrapper -> wrapper
                        .nested(queued -> queued.in(GenerationTask::getStatus, "CREATED", "QUEUED")
                                .lt(GenerationTask::getUpdateTime, LocalDateTime.now().minusMinutes(1)))
                        .or().nested(unsettled -> unsettled.in(GenerationTask::getStatus, "SUCCEEDED", "FAILED", "CANCELLED")
                                .isNull(GenerationTask::getBeanCost)))
                .orderByAsc(GenerationTask::getUpdateTime).last("LIMIT 100"));
        for (GenerationTask task : pending) {
            try {
                queue.send("generation-task", Map.of("generationTaskId", task.getId()));
                tasks.update(new LambdaUpdateWrapper<GenerationTask>().eq(GenerationTask::getId, task.getId())
                        .eq(GenerationTask::getStatus, task.getStatus())
                        .set(GenerationTask::getUpdateTime, LocalDateTime.now()));
            } catch (RuntimeException unavailable) {
                log.warn("图片任务队列恢复待重试: generationTaskId={}", task.getId());
                break;
            }
        }
    }
}
