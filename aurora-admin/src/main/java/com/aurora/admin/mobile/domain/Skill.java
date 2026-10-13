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
 * Skill 配置实体(用户 @ 激活时注入提示词)。
 * inject_prompt 是送给大模型的原始文本,入库前由 SkillServiceImpl 做 htmlUnescape 反转义;
 * capabilities 逗号分隔(text/vision/image/video);逻辑删走 del_flag(@TableLogic)。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("skill")
public class Skill implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Skill ID
     */
    @TableId(value = "skill_id", type = IdType.AUTO)
    private Long skillId;

    /**
     * Skill 名称
     */
    private String name;

    /**
     * 图标 URL
     */
    private String icon;

    /**
     * 说明
     */
    private String description;

    /**
     * 注入提示词(送大模型原始文本,入库前 htmlUnescape)
     */
    private String injectPrompt;

    /**
     * 可用能力范围(逗号分隔:text/vision/image/video)
     */
    private String capabilities;

    /**
     * 排序(小在前)
     */
    private Integer sort;

    /**
     * 状态(0启用/1停用)
     */
    private String status;

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
