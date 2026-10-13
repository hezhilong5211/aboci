package com.aurora.ai.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 单张作品详情：供生成页查看原提示词、实际参考图和原生成参数。
 */
@Data
@AllArgsConstructor
public class WorkItemDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String taskId;
    private Integer seq;
    private String imageUrl;
    private String prompt;
    private List<String> referenceImages;
    private String mode;
    private String resolution;
    private String aspectRatio;
    private Long beanCost;
    private String createTime;
    /** 旧依赖任务可查看，但不能自动恢复或重放；用户可另行主动选择素材。 */
    private boolean replaySupported;
    private String replayUnavailableReason;
}
