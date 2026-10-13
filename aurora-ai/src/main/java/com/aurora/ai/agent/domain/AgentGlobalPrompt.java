package com.aurora.ai.agent.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 全局提示词只读映射(aurora-ai 内直查物理表 global_prompt,供提示词组装读取)。
 * 仅读 is_active='1' 的生效记录;实体写侧在 aurora-admin。
 * 规避 ai→admin 循环依赖(PRD D3 / §5.6)。
 */
@Data
@TableName("global_prompt")
public class AgentGlobalPrompt implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId("id")
    private Long id;

    /**
     * 全局提示词内容(写侧已 htmlUnescape,读出即原文)
     */
    private String content;

    /**
     * 是否生效(1 生效,全局唯一)
     */
    @TableField("is_active")
    private String isActive;

    /**
     * 逻辑删除标志(0 存在 / 2 删除)
     */
    @TableLogic
    private String delFlag;
}
