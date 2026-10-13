-- GPT-5.6 Sol Agent 模型占位配置。
-- API Key/Base URL 复用现有云雾模型，禁止在迁移中硬编码凭据。
-- 计费三档需由后台确认后填写，因此初始不启用、不设默认，避免零价调用。
INSERT INTO `sys_ai_model`
    (`model_name`, `api_key`, `base_url`, `model_deployment`, `temperature`, `max_tokens`,
     `provider`, `model_type`, `is_active`, `is_default`, `sort_order`, `create_time`, `update_time`)
SELECT 'GPT-5.6 Sol(Agent)', `src`.`api_key`, `src`.`base_url`, 'gpt-5.6-sol', 0.7, 8192,
       'yunwu', 'chat', 0, 0, -10, NOW(), NOW()
FROM `sys_ai_model` `src`
WHERE `src`.`provider` = 'yunwu'
  AND `src`.`api_key` IS NOT NULL
  AND `src`.`api_key` <> ''
  AND NOT EXISTS (
      SELECT 1 FROM `sys_ai_model` `target`
      WHERE `target`.`model_deployment` = 'gpt-5.6-sol'
        AND `target`.`model_type` = 'chat'
  )
ORDER BY `src`.`is_default` DESC, `src`.`sort_order` ASC, `src`.`id` ASC
LIMIT 1;
