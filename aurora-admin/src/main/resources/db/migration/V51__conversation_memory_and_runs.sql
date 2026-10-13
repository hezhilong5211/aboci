ALTER TABLE conversation
    ADD COLUMN run_id VARCHAR(64) DEFAULT NULL COMMENT 'Latest client request ID',
    ADD COLUMN run_status VARCHAR(32) DEFAULT NULL COMMENT 'Latest orchestration state';

CREATE TABLE agent_chat_run (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    conversation_id BIGINT NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    message_id BIGINT DEFAULT NULL,
    assistant_message_id BIGINT DEFAULT NULL,
    model_id BIGINT DEFAULT NULL,
    image_model_id BIGINT DEFAULT NULL,
    billing_snapshot TEXT DEFAULT NULL,
    status VARCHAR(32) NOT NULL,
    text LONGTEXT,
    thinking LONGTEXT,
    result_json LONGTEXT,
    clarification_json TEXT,
    error_message VARCHAR(500),
    heartbeat_time DATETIME NOT NULL,
    create_time DATETIME NOT NULL,
    update_time DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_request (user_id, request_id),
    KEY idx_conversation_run (conversation_id, id),
    KEY idx_run_recovery (status, heartbeat_time),
    CONSTRAINT fk_chat_run_conversation FOREIGN KEY (conversation_id) REFERENCES conversation(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE conversation_memory (
    conversation_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    covered_message_id BIGINT DEFAULT NULL,
    summary_json LONGTEXT,
    image_index_json LONGTEXT,
    update_time DATETIME NOT NULL,
    PRIMARY KEY (conversation_id),
    CONSTRAINT fk_memory_conversation FOREIGN KEY (conversation_id) REFERENCES conversation(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
