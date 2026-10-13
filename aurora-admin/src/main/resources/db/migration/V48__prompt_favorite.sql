-- 用户提示词收藏。
-- 独立于会话生命周期，默认不回填历史提示词；用户主动删除前永久保留。

CREATE TABLE `prompt_favorite` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '收藏ID',
    `user_id`      BIGINT       NOT NULL COMMENT '移动端用户ID',
    `name`         VARCHAR(64)  NOT NULL COMMENT '收藏备注名称',
    `content`      MEDIUMTEXT   NOT NULL COMMENT '提示词原文',
    `content_hash` CHAR(64)     NOT NULL COMMENT '规范化提示词SHA-256',
    `create_by`    VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '创建者',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`    VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '更新者',
    `update_time`  DATETIME     NULL COMMENT '更新时间',
    `remark`       VARCHAR(500) NULL COMMENT '备注',
    `del_flag`     CHAR(1)      NOT NULL DEFAULT '0' COMMENT '删除标志（0存在 2删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_prompt_favorite_user_content` (`user_id`, `content_hash`),
    KEY `idx_prompt_favorite_user_status_time` (`user_id`, `del_flag`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户提示词收藏';
