package com.aurora.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * Agent 末尾结构化产出(PRD §9.2 result 事件载荷 data)。
 * 各段全可选,前端「有什么渲染什么」;null 段不序列化输出。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 标题(可选)
     */
    private String title;

    /**
     * 正文(可选)
     */
    private String body;

    /**
     * 标签(可选)
     */
    private List<String> tags;

    /**
     * 图引用(可选);每项指向一个出图任务的某一张图
     */
    private List<ImageRef> images;

    /**
     * 图引用:关联出图任务 taskId 与图序 seq(从 1 起,1=封面)。
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ImageRef implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 出图任务 taskId
         */
        private String taskId;

        /**
         * 图序(从 1 起)
         */
        private int seq;
    }
}
