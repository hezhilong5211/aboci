package com.aurora.ai.agent.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 模型显式请求澄清时创建的一次性图片引用范围。 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("reference_scope")
public class ReferenceScope implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String scopeId;
    private Long conversationId;
    private Long userId;
    private Long sourceMessageId;
    private String attachmentIds;
    private String question;
    private Integer allowAdditionalImages;
    /** 已确认答案、已问问题与当前问题选项的紧凑 JSON 快照。 */
    private String contextJson;
    /** 当前澄清轮数，从 1 开始。 */
    private Integer roundNo;
    /** 过期后不得再被后续消息消费。 */
    private LocalDateTime expiresAt;
    private String status;
    private Long consumedByMessageId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
