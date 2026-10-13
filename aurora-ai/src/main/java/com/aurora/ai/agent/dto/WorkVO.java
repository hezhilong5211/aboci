package com.aurora.ai.agent.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 「我的作品」批次摘要。
 * 列表只返回规整展示所需字段，完整提示词与参考图在批次详情中读取。
 */
@Data
public class WorkVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 任务主键 id
     */
    private Long id;

    /**
     * Redis 队列 taskId
     */
    private String taskId;

    /**
     * 兼容旧客户端的摘要字段，不返回长提示词全文。
     */
    private String prompt;

    /** 生成页提示词或 Agent 执行目标摘要。 */
    private String promptSummary;

    /** 来源：CREATE_PAGE / AGENT。 */
    private String sourceType;

    /**
     * 成功的 /profile/*.png URL 列表
     */
    private List<String> resultUrls;

    /** 卡片优先使用的预览图 URL；旧数据回退 resultUrls */
    private List<String> previewUrls;

    /**
     * 实际成功张数
     */
    private Integer successCount;

    /** 批次原计划张数。 */
    private Integer plannedCount;

    /** 批次失败张数。 */
    private Integer failedCount;

    /** 批次是否实际使用过参考图。 */
    private Boolean hasReferences;

    /**
     * 创建时间(格式化 yyyy-MM-dd HH:mm:ss)
     */
    private String createTime;
}
