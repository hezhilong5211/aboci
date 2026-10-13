-- 会话历史按 conversation_id 过滤、按 id 倒序游标翻页，避免长会话扫描后再排序。
ALTER TABLE `message`
    ADD KEY `idx_conversation_id` (`conversation_id`, `id`);
