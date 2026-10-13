package com.aurora.ai.agent.dto;

import lombok.Data;

/** 豆子充值成功后恢复已落库用户消息。 */
@Data
public class AgentResumeRequest {

    private String requestId;
    private Long conversationId;
    private Long sourceMessageId;
    private String skill;
    private String clarificationScopeId;
    private String clarificationOptionId;
}
