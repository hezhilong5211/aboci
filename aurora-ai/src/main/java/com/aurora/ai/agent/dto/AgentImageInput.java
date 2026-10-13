package com.aurora.ai.agent.dto;

import lombok.Data;

/** 对话图片联合入参：上传图片或当前用户已生成的图片。 */
@Data
public class AgentImageInput {

    /** upload / generated */
    private String type;

    /** type=upload 时的上传图片 URL */
    private String url;

    /** type=generated 时的生成任务号 */
    private String taskId;

    /** type=generated 时的结果序号，从 1 开始 */
    private Integer seq;
}
