package com.aurora.admin.mobile.dto;

import lombok.Data;

/**
 * 模板上下架快捷切换入参
 */
@Data
public class TemplateStatusDTO {

    /**
     * 模板ID
     */
    private Long templateId;

    /**
     * 上下架状态(0上架/1下架)
     */
    private String status;
}
