package com.aurora.framework.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 任务生产者
 * 负责将任务消息发送到 Redis Streams
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskProducer {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 任务状态存储的 Key 前缀
     */
    private static final String TASK_STATUS_PREFIX = "task:status:";

    /**
     * 任务状态过期时间（7 天）
     */
    private static final long TASK_EXPIRE_DAYS = 7;

    /**
     * 发送任务到队列
     * 
     * @param streamKey 队列名称（如 "stream:graph-extract"）
     * @param taskType  任务类型
     * @param payload   任务数据
     * @return 任务消息（包含生成的 taskId）
     */
    public TaskMessage send(String streamKey, String taskType, Map<String, Object> payload) {
        // 创建任务消息
        TaskMessage message = TaskMessage.create(taskType, payload);

        try {
            // 保存任务状态到 Redis Hash
            saveTaskStatus(message);

            // 发送到 Redis Stream
            String messageJson = objectMapper.writeValueAsString(message);

            ObjectRecord<String, String> record = StreamRecords
                    .newRecord()
                    .ofObject(messageJson)
                    .withStreamKey(streamKey);

            RecordId recordId = redisTemplate.opsForStream().add(record);

            log.info("✅ 任务已发送: taskId={}, streamKey={}, recordId={}",
                    message.getTaskId(), streamKey, recordId);

            return message;

        } catch (Exception e) {
            log.error("❌ 发送任务失败: taskType={}, error={}", taskType, e.getMessage(), e);
            message.markFailed("发送失败: " + e.getMessage());
            saveTaskStatus(message);
            throw new RuntimeException("发送任务失败", e);
        }
    }

    /**
     * 快速发送任务（使用默认队列名）
     */
    public TaskMessage send(String taskType, Map<String, Object> payload) {
        String streamKey = "stream:" + taskType;
        return send(streamKey, taskType, payload);
    }

    /**
     * 保存任务状态到 Redis
     */
    public void saveTaskStatus(TaskMessage message) {
        try {
            String key = TASK_STATUS_PREFIX + message.getTaskId();
            String json = objectMapper.writeValueAsString(message);
            redisTemplate.opsForValue().set(key, json, TASK_EXPIRE_DAYS, TimeUnit.DAYS);
        } catch (Exception e) {
            log.error("保存任务状态失败: taskId={}", message.getTaskId(), e);
        }
    }

    /**
     * 更新任务状态
     */
    public void updateTaskStatus(String taskId, TaskStatus status, Integer progress, String message) {
        try {
            String key = TASK_STATUS_PREFIX + taskId;
            String json = redisTemplate.opsForValue().get(key);

            if (json != null) {
                TaskMessage task = objectMapper.readValue(json, TaskMessage.class);
                task.setStatus(status);
                if (progress != null) {
                    task.setProgress(progress);
                }
                if (message != null) {
                    task.setResultMessage(message);
                }
                saveTaskStatus(task);
            }
        } catch (Exception e) {
            log.error("更新任务状态失败: taskId={}", taskId, e);
        }
    }
}
