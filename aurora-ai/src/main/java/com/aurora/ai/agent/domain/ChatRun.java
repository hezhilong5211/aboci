package com.aurora.ai.agent.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_chat_run")
public class ChatRun {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long conversationId;
    private String requestId;
    private String requestFingerprint;
    private Long messageId;
    private Long assistantMessageId;
    private Long modelId;
    private Long imageModelId;
    private String billingSnapshot;
    private String status;
    private String completionStatus;
    private String text;
    private String thinking;
    private String resultJson;
    private String clarificationJson;
    private String errorMessage;
    private LocalDateTime heartbeatTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
