package com.aurora.ai.agent.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 用户消息中的有序图片附件。 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("message_attachment")
public class MessageAttachment implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String attachmentId;
    private Long messageId;
    private Long conversationId;
    private Long userId;
    private Integer position;
    private String sourceType;
    private String profileUrl;
    private String sourceTaskId;
    private Integer sourceSeq;
    private LocalDateTime createTime;
}
