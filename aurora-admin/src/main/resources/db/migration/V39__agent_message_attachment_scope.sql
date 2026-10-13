-- Agent 图片引用闭环：稳定附件身份 + 一次性澄清引用范围。

CREATE TABLE IF NOT EXISTS `message_attachment` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `attachment_id`  VARCHAR(64)  NOT NULL COMMENT '对话内稳定附件ID',
    `message_id`     BIGINT       NOT NULL COMMENT '所属用户消息ID',
    `conversation_id` BIGINT      NOT NULL COMMENT '所属会话ID',
    `user_id`        BIGINT       NOT NULL COMMENT '所属用户ID',
    `position`       INT          NOT NULL COMMENT '原消息内顺序，从1开始',
    `source_type`    VARCHAR(20)  NOT NULL COMMENT '来源：UPLOAD/GENERATED',
    `profile_url`    TEXT         NOT NULL COMMENT '实际可读图片地址',
    `source_task_id` VARCHAR(64)  DEFAULT NULL COMMENT '来源生成任务ID',
    `source_seq`     INT          DEFAULT NULL COMMENT '来源生成结果序号',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_attachment_id` (`attachment_id`),
    UNIQUE KEY `uk_message_position` (`message_id`, `position`),
    KEY `idx_conversation` (`conversation_id`),
    KEY `idx_user` (`user_id`),
    KEY `idx_source_task` (`source_task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Agent用户消息图片附件';

CREATE TABLE IF NOT EXISTS `reference_scope` (
    `id`                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `scope_id`              VARCHAR(64)  NOT NULL COMMENT '一次性引用范围ID',
    `conversation_id`       BIGINT       NOT NULL COMMENT '所属会话ID',
    `user_id`               BIGINT       NOT NULL COMMENT '所属用户ID',
    `source_message_id`     BIGINT       NOT NULL COMMENT '原始需求消息ID',
    `attachment_ids`        TEXT         NOT NULL COMMENT '允许继续使用的附件ID数组',
    `question`              TEXT         DEFAULT NULL COMMENT '本次澄清问题',
    `allow_additional_images` TINYINT    NOT NULL DEFAULT 0 COMMENT '下一轮是否允许追加新图',
    `status`                VARCHAR(20)  NOT NULL COMMENT '状态：OPEN/CONSUMED/CANCELLED',
    `consumed_by_message_id` BIGINT      DEFAULT NULL COMMENT '消费该范围的用户消息ID',
    `create_time`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_scope_id` (`scope_id`),
    KEY `idx_open_scope` (`conversation_id`, `user_id`, `status`, `id`),
    KEY `idx_source_message` (`source_message_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Agent一次性澄清图片引用范围';
