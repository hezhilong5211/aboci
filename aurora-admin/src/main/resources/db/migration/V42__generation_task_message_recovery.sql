ALTER TABLE `generation_task`
    MODIFY COLUMN `message_id` BIGINT DEFAULT NULL COMMENT '触发任务的用户消息ID(独立生成页为空)',
    ADD KEY `idx_message_recovery` (`user_id`, `conversation_id`, `message_id`, `create_time`);
