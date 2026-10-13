package com.aurora.ai.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 「我的作品」批次详情，完整展示计划图片、提示词、参考图和生成参数。
 */
@Data
@AllArgsConstructor
public class WorkBatchDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String taskId;
    private String sourceType;
    private String batchPrompt;
    private Integer plannedCount;
    private Integer successCount;
    private Integer failedCount;
    private String resolution;
    private String aspectRatio;
    private Long totalBeanCost;
    private String createTime;
    private List<WorkBatchItemVO> items;
}
