package com.aurora.admin.mobile.dto;

import lombok.Data;

/**
 * 全局提示词「保存新版本并即时生效」入参
 */
@Data
public class GlobalPromptSaveDTO {

    /**
     * 提示词内容
     */
    private String content;
}
