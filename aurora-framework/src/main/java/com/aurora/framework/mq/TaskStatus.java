package com.aurora.framework.mq;

/**
 * 任务状态枚举
 */
public enum TaskStatus {

    /**
     * 待处理 - 任务已提交，等待消费者处理
     */
    PENDING("待处理"),

    /**
     * 处理中 - 消费者正在处理任务
     */
    RUNNING("处理中"),

    /**
     * 限流等待 - API 请求繁忙，正在排队等待
     */
    RATE_LIMITED("限流等待"),

    /**
     * 成功 - 任务处理完成
     */
    SUCCESS("成功"),

    /**
     * 失败 - 任务处理失败
     */
    FAILED("失败"),

    /**
     * 已取消 - 任务被用户取消
     */
    CANCELLED("已取消");

    private final String description;

    TaskStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
