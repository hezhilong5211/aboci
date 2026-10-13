package com.aurora.ai.agent.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 出图任务视图对象(轮询出图接口出参,PRD §十 generation/{taskId})。
 * resultUrls 为已反序列化的成功 URL 列表;errorMsg 已脱敏。
 */
@Data
public class GenerationTaskVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Redis 队列 taskId
     */
    private String taskId;

    /**
     * 状态(CREATED/QUEUED/RUNNING/SUCCEEDED/FAILED/CANCELLED)
     */
    private String status;

    /**
     * 请求张数
     */
    private Integer n;

    /**
     * 实际成功张数
     */
    private Integer successCount;

    /**
     * 成功的 /profile/*.png URL 列表
     */
    private List<String> resultUrls;

    /** 卡片优先使用的预览图 URL；旧数据回退 resultUrls */
    private List<String> previewUrls;

    /**
     * 失败原因(脱敏)
     */
    private String errorMsg;
}
