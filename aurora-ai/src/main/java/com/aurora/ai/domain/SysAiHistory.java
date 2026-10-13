package com.aurora.ai.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 对话及图像生成历史记录实体类
 */
@Data
@TableName("sys_ai_history")
public class SysAiHistory implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 会话 ID，用于对消息进行分组
     */
    private String sessionId;

    /**
     * 使用的模型 ID
     */
    private Long modelId;

    /**
     * 使用的模型名称
     */
    private String modelName;

    /**
     * 角色：user (用户), assistant (助手)
     */
    private String role;

    /**
     * 内容：文本或图像 URL/Base64
     */
    private String content;

    /**
     * 消息类型：text (文本), image (图像)
     */
    private String msgType;

    /**
     * 原始提示词（对图像生成很有用）
     */
    private String prompt;

    /**
     * 来源类型：chat (普通对话)
     */
    private String sourceType;

    /**
     * 来源 ID（预留，普通对话为空）
     */
    private Long sourceId;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
