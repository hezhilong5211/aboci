package com.aurora.ai.agent.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 内容审核记录实体(送审存证流水,V14)。
 * 流水表:无审计五件套、无逻辑删(不标 @TableLogic),仅 create_time;
 * suggest 对齐 {@link com.aurora.ai.agent.security.ContentCheckResult} 的建议值(pass/review/risky/error);
 * result 为最终处置(PASS/REJECT/ERROR);content_snippet 为送审文本截断存证;
 * task_id/trace_id 预留媒体异步审核,本期文本(object_type=text)不填。
 */
@Data
@NoArgsConstructor
@TableName("audit_record")
public class AuditRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 审核记录ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 送审用户(mobile_user.id)
     */
    private Long userId;

    /**
     * 对象类型(text文本/media媒体,本期 text)
     */
    private String objectType;

    /**
     * 业务类型(chat_input 对话输入)
     */
    private String bizType;

    /**
     * 关联会话ID(对话内输入,可空)
     */
    private Long conversationId;

    /**
     * 关联任务ID(预留媒体异步审核)
     */
    private String taskId;

    /**
     * 送审文本截断存证(超长截断)
     */
    private String contentSnippet;

    /**
     * 审核建议(pass/review/risky/error)
     */
    private String suggest;

    /**
     * 命中标签(可空)
     */
    private String label;

    /**
     * 最终处置(PASS/REJECT/ERROR)
     */
    private String result;

    /**
     * 违规提示/原因(脱敏,可空)
     */
    private String reason;

    /**
     * 链路追踪ID(预留媒体异步)
     */
    private String traceId;

    /**
     * 创建时间(自动填充;DB default 兜底)
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
