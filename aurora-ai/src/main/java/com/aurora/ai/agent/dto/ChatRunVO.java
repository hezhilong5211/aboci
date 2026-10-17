package com.aurora.ai.agent.dto;

import com.aurora.ai.dto.AgentClarification;
import com.aurora.ai.dto.AgentResult;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ChatRunVO {
    private String runId;
    private String requestId;
    private Long conversationId;
    private Long messageId;
    private Long assistantMessageId;
    private Long modelId;
    private Long imageModelId;
    private String imageResolution;
    private String status;
    private String text;
    private String thinking;
    private AgentResult result;
    private AgentClarification clarification;
    private String errorMessage;
    private String errorCode;
    private String action;
    private LocalDateTime updatedTime;
    private List<GenerationTaskVO> generationTasks;
}
