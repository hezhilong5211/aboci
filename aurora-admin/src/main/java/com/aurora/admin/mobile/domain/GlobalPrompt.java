package com.aurora.admin.mobile.domain;

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
 * 全局提示词实体(全局唯一生效 + 历史版本)。
 * content 是送给大模型的原始文本,入库前由 GlobalPromptServiceImpl 做 htmlUnescape 反转义;
 * is_active 全局唯一仅一条为 1,切换由 Service 事务保证(先全表置0再置目标为1)。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("global_prompt")
public class GlobalPrompt implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 提示词内容(送大模型原始文本,入库前 htmlUnescape)
     */
    private String content;

    /**
     * 是否生效(1生效/0历史),全局唯一仅一条为1
     */
    private String isActive;

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
