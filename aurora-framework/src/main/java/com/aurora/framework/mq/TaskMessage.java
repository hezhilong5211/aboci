package com.aurora.framework.mq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 任务消息实体
 * 用于在 Redis Streams 中传递任务信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 任务 ID（全局唯一）
     */
    private String taskId;

    /**
     * 任务类型（如 image-generate）
     */
    private String taskType;

    /**
     * 任务状态
     */
    private TaskStatus status;

    /**
     * 任务负载数据（JSON 格式存储）
     */
    private Map<String, Object> payload;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 开始处理时间
     */
    private LocalDateTime startTime;

    /**
     * 完成时间
     */
    private LocalDateTime endTime;

    /**
     * 进度（0-100）
     */
    private Integer progress;

    /**
     * 结果消息
     */
    private String resultMessage;

    /**
     * 错误消息（失败时）
     */
    private String errorMessage;

    /**
     * 重试次数
     */
    private Integer retryCount;

    /**
     * 创建新任务
     */
    public static TaskMessage create(String taskType, Map<String, Object> payload) {
        return TaskMessage.builder()
                .taskId(java.util.UUID.randomUUID().toString().replace("-", ""))
                .taskType(taskType)
                .status(TaskStatus.PENDING)
                .payload(payload)
                .createTime(LocalDateTime.now())
                .progress(0)
                .retryCount(0)
                .build();
    }

    /**
     * 标记为处理中
     */
    public void markRunning() {
        this.status = TaskStatus.RUNNING;
        this.startTime = LocalDateTime.now();
    }

    /**
     * 标记为成功
     */
    public void markSuccess(String message) {
        this.status = TaskStatus.SUCCESS;
        this.endTime = LocalDateTime.now();
        this.progress = 100;
        this.resultMessage = message;
    }

    /**
     * 标记为失败
     */
    public void markFailed(String error) {
        this.status = TaskStatus.FAILED;
        this.endTime = LocalDateTime.now();
        this.errorMessage = error;
    }

    /**
     * 更新进度
     */
    public void updateProgress(int progress, String message) {
        this.progress = Math.min(100, Math.max(0, progress));
        this.resultMessage = message;
    }
}
