package com.aurora.admin.mobile.dto;

import lombok.Data;

/**
 * Skill 启停快捷切换入参
 */
@Data
public class SkillStatusDTO {

    /**
     * Skill ID
     */
    private Long skillId;

    /**
     * 状态(0启用/1停用)
     */
    private String status;
}
