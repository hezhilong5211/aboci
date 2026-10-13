package com.aurora.ai.agent.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 对话消息明细实体(轻量流水,PRD §5.2,对齐 sys_ai_history 风格)。
 * 无审计五件套、无逻辑删(随会话过期级联清理),仅 create_time;
 * content 文本,assistant 结构化产出存其 JSON 串(content_type=structured),
 * 结构化追问使用 content_type=clarification 并在 media 保留卡片快照;
 * media 存用户有序图片引用或产出 images 引用的 JSON 串;
 * token_usage/bean_cost 预留计费,本期 null。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("message")
public class Message implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属会话
     */
    private Long conversationId;

    /**
     * 所属用户(冗余,便于按人查/清理)
     */
    private Long userId;

    /**
     * 角色(user / assistant / tool)
     */
    private String role;

    /**
     * 内容(文本;assistant 结构化产出存其 JSON 串)
     */
    private String content;

    /**
     * 内容类型(text / structured / clarification)
     */
    private String contentType;

    /**
     * JSON:用户图片引用或产出 images 引用
     */
    private String media;

    /**
     * role=tool 时:generate_images / submit_result
     */
    private String toolName;

    /**
     * 工具调用关联 id(供审计/回灌)
     */
    private String toolCallId;

    /**
     * 预留计费,本期 null
     */
    private Integer tokenUsage;

    /**
     * 预留计费,本期 null
     */
    private Integer beanCost;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
