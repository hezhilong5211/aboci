package com.aurora.ai.agent.dto;

import com.aurora.ai.agent.domain.GenerationTask;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private Long modelId;
    private String modelName;
    private String resolution;
    private String aspectRatio;
    private Long beanPerImage;
    private Long prepaidBeans;
    private Integer beanCost;

    /** Public projection shared by task polling and durable chat recovery. */
    public static GenerationTaskVO from(GenerationTask task, ObjectMapper mapper) {
        GenerationTaskVO vo = new GenerationTaskVO();
        vo.setTaskId(task.getTaskId());
        vo.setStatus(task.getStatus());
        vo.setN(task.getN());
        vo.setSuccessCount(task.getSuccessCount());
        List<String> urls;
        try {
            urls = task.getResultUrls() == null ? List.of()
                    : mapper.readValue(task.getResultUrls(), new TypeReference<List<String>>() {});
        } catch (Exception e) { urls = List.of(); }
        if (urls == null) urls = List.of();
        vo.setResultUrls(urls);
        vo.setPreviewUrls(urls.stream().map(url -> url != null && url.contains("/profile/generated/") && url.endsWith(".png")
                ? url.substring(0, url.length() - 4) + ".preview.jpg" : url).toList());
        vo.setErrorMsg(task.getErrorMsg());
        vo.setModelId(task.getModelId());
        vo.setModelName(task.getModelNameSnapshot());
        vo.setResolution(task.getResolution());
        vo.setAspectRatio(task.getAspectRatio());
        vo.setBeanPerImage(task.getBeanPerImageSnapshot());
        vo.setPrepaidBeans(task.getPrepaidBeans());
        vo.setBeanCost(task.getBeanCost());
        return vo;
    }
}
