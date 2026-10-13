ALTER TABLE agent_chat_run
    ADD COLUMN completion_status VARCHAR(32) DEFAULT NULL COMMENT 'Text outcome while image tasks remain active';

UPDATE agent_chat_run
SET completion_status = CASE WHEN status = 'GENERATING' THEN 'COMPLETED' ELSE status END
WHERE status NOT IN ('QUEUED', 'RUNNING');
