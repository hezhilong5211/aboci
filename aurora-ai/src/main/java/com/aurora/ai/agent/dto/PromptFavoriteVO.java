package com.aurora.ai.agent.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 提示词收藏视图。
 */
@Data
public class PromptFavoriteVO {

    private Long id;

    private String name;

    private String content;

    private LocalDateTime createTime;
}
