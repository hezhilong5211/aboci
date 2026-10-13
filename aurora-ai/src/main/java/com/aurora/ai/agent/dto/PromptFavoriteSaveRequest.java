package com.aurora.ai.agent.dto;

import lombok.Data;

/**
 * 提示词收藏请求。
 */
@Data
public class PromptFavoriteSaveRequest {

    private String name;

    private String content;
}
