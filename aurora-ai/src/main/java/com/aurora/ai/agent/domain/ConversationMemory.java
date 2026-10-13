package com.aurora.ai.agent.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("conversation_memory")
public class ConversationMemory {
    @TableId
    private Long conversationId;
    private Long userId;
    private Long coveredMessageId;
    private String summaryJson;
    private String imageIndexJson;
    private LocalDateTime updateTime;
}
