package com.aurora.ai.agent.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * Skill 只读映射(aurora-ai 内直查物理表 skill,供提示词组装跨模块读)。
 * 仅映射组装所需字段,不做写操作;实体写侧在 aurora-admin。
 * 规避 ai→admin 循环依赖(PRD D3 / §5.6)。
 */
@Data
@TableName("skill")
public class AgentSkill implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId("skill_id")
    private Long skillId;

    /**
     * Skill 名称(@ 激活按名命中)
     */
    private String name;

    /**
     * 图标 URL(供 @ 列表展示)
     */
    private String icon;

    /**
     * 说明(供 @ 列表展示)
     */
    private String description;

    /**
     * 注入提示词(命中后拼入 system message;写侧已 htmlUnescape,读出即原文)
     */
    @TableField("inject_prompt")
    private String injectPrompt;

    /**
     * 能力描述
     */
    private String capabilities;

    /**
     * 状态(0 启用)
     */
    private String status;

    /**
     * 逻辑删除标志(0 存在 / 2 删除)
     */
    @TableLogic
    private String delFlag;
}
