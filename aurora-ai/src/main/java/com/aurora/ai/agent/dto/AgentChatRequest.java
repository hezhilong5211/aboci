package com.aurora.ai.agent.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.List;

/**
 * Agent 主对话请求 DTO(PRD §十 /api/mobile/agent/chat/stream 入参)。
 * 不含 userId:用户身份由 MobileJwtFilter 注入 @RequestAttribute("mobileUserId"),防越权(R-DTO)。
 */
@Data
public class AgentChatRequest {

    /**
     * 客户端为本次请求生成的唯一标识；用于消息落库前的排队阶段显式停止。
     */
    private String requestId;

    /**
     * 会话 id;为空则新建会话
     */
    private Long conversationId;

    /**
     * 模型 id;为空走默认 active chat 模型
     */
    private Long modelId;

    /** 图片工具使用的模型；为空时使用默认图像模型。 */
    private Long imageModelId;

    /** 用户选择的出图清晰度；为空时使用所选图像模型默认值。 */
    private String imageResolution;

    /**
     * 用户消息文本
     */
    private String message;

    /**
     * 用户上传图的可访问 URL 列表(可空,最多 9 张)。
     * 数组顺序即图片编号:第 1 项为图片 1,后续依次编号。
     */
    private List<String> images;

    /**
     * 有序图片联合入参；新客户端优先使用，后端解析后仍按数组顺序编号。
     */
    private List<AgentImageInput> imageInputs;

    /**
     * @ 激活的 Skill 名称(可空)
     */
    private String skill;

    /**
     * 回答结构化澄清时由客户端原样回传；为空表示当前消息是新任务，不能继承旧图片范围。
     */
    private String clarificationScopeId;

    /** 点击快捷选项时回传的选项 id；自由输入时为空。 */
    private String clarificationOptionId;

    /** 仅由服务端恢复入口设置，普通对话请求不能直接注入。 */
    @JsonIgnore
    private Long resumeMessageId;
}
