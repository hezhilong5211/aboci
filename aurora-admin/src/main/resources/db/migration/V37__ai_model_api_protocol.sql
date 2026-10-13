-- 模型级接口协议：历史模型默认沿用 Chat Completions，GPT-5.6 Sol 改走 Responses。
ALTER TABLE `sys_ai_model`
    ADD COLUMN `api_protocol` VARCHAR(32) NOT NULL DEFAULT 'chat_completions'
        COMMENT '接口协议：chat_completions/responses' AFTER `model_type`;

UPDATE `sys_ai_model`
SET `api_protocol` = 'responses'
WHERE `model_type` = 'chat'
  AND `model_deployment` = 'gpt-5.6-sol';
