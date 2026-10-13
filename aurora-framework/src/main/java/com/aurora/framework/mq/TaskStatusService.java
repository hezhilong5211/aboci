package com.aurora.framework.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 任务状态服务
 * 提供任务状态查询和管理功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskStatusService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String TASK_STATUS_PREFIX = "task:status:";

    /**
     * 获取任务状态
     * 
     * @param taskId 任务 ID
     * @return 任务消息（可能为空）
     */
    public Optional<TaskMessage> getTask(String taskId) {
        try {
            String key = TASK_STATUS_PREFIX + taskId;
            String json = redisTemplate.opsForValue().get(key);

            if (json != null) {
                TaskMessage task = objectMapper.readValue(json, TaskMessage.class);
                return Optional.of(task);
            }
        } catch (Exception e) {
            log.error("获取任务状态失败: taskId={}", taskId, e);
        }
        return Optional.empty();
    }

    /**
     * 获取任务状态枚举
     */
    public TaskStatus getStatus(String taskId) {
        return getTask(taskId)
                .map(TaskMessage::getStatus)
                .orElse(null);
    }

    /**
     * 获取任务进度
     */
    public Integer getProgress(String taskId) {
        return getTask(taskId)
                .map(TaskMessage::getProgress)
                .orElse(0);
    }

    /**
     * 检查任务是否完成
     */
    public boolean isCompleted(String taskId) {
        TaskStatus status = getStatus(taskId);
        return status == TaskStatus.SUCCESS ||
                status == TaskStatus.FAILED ||
                status == TaskStatus.CANCELLED;
    }

    /**
     * 检查任务是否成功
     */
    public boolean isSuccess(String taskId) {
        return getStatus(taskId) == TaskStatus.SUCCESS;
    }

    /**
     * 取消任务
     */
    public boolean cancelTask(String taskId) {
        try {
            Optional<TaskMessage> taskOpt = getTask(taskId);
            if (taskOpt.isPresent()) {
                TaskMessage task = taskOpt.get();
                if (task.getStatus() == TaskStatus.PENDING || task.getStatus() == TaskStatus.RUNNING) {
                    task.setStatus(TaskStatus.CANCELLED);
                    task.setResultMessage("任务已取消");
                    saveTask(task);
                    log.info("任务已取消: taskId={}", taskId);
                    return true;
                }
            }
        } catch (Exception e) {
            log.error("取消任务失败: taskId={}", taskId, e);
        }
        return false;
    }

    /**
     * 删除任务状态
     */
    public void deleteTask(String taskId) {
        String key = TASK_STATUS_PREFIX + taskId;
        redisTemplate.delete(key);
    }

    /**
     * 保存任务状态
     */
    private void saveTask(TaskMessage task) {
        try {
            String key = TASK_STATUS_PREFIX + task.getTaskId();
            String json = objectMapper.writeValueAsString(task);
            redisTemplate.opsForValue().set(key, json);
        } catch (Exception e) {
            log.error("保存任务状态失败: taskId={}", task.getTaskId(), e);
        }
    }
}
