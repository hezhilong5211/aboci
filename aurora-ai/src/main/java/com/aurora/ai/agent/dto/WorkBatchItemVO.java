package com.aurora.ai.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 作品批次中的单个计划槽位，失败图片也保留原序号。
 */
@Data
@AllArgsConstructor
public class WorkBatchItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer seq;
    private String status;
    private String imageUrl;
    private String previewUrl;
    private String executionTarget;
    private String userPrompt;
    private String fullPrompt;
    private List<WorkReferenceVO> references;
    private String mode;
    private Long beanCost;
}
