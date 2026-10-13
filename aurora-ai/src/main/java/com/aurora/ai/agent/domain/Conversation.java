package com.aurora.ai.agent.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 对话会话头实体(业务主表,PRD §5.1)。
 * user_id 取自登录态,非入参;expire_time = last_message_time + 30 天供清理;
 * last_message_time 用于会话列表倒序;逻辑删走 del_flag(@TableLogic),审计五字段自动回填。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("conversation")
public class Conversation implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属用户(mobile_user.id,取自登录态)
     */
    private Long userId;

    /**
     * 标题(首条消息摘要或「新对话」)
     */
    private String title;

    /**
     * DeepSeek 模型 id(默认取 active chat 模型)
     */
    private Long modelId;

    /**
     * 最近消息时间(列表倒序用)
     */
    private LocalDateTime lastMessageTime;

    /**
     * 过期时间(最后使用时间 + 30 天,清理用)
     */
    private LocalDateTime expireTime;

    /**
     * 创建者
     */
    @TableField(fill = FieldFill.INSERT)
    private String createBy;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新者
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 备注
     */
    private String remark;

    /**
     * 逻辑删除标记(0存在/2删除)
     */
    @TableLogic
    private String delFlag;
}
