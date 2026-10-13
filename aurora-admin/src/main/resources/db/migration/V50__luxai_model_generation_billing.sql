ALTER TABLE sys_ai_model ADD COLUMN image_quality VARCHAR(16) DEFAULT NULL;

ALTER TABLE generation_task
    ADD COLUMN request_id VARCHAR(64) DEFAULT NULL,
    ADD COLUMN model_name_snapshot VARCHAR(255) DEFAULT NULL,
    ADD COLUMN provider_snapshot VARCHAR(64) DEFAULT NULL,
    ADD COLUMN base_url_snapshot VARCHAR(512) DEFAULT NULL,
    ADD COLUMN model_deployment_snapshot VARCHAR(255) DEFAULT NULL,
    ADD COLUMN image_quality_snapshot VARCHAR(16) DEFAULT NULL,
    ADD COLUMN bean_per_image_snapshot BIGINT DEFAULT NULL,
    ADD COLUMN prepaid_beans BIGINT DEFAULT NULL,
    ADD UNIQUE KEY uk_generation_user_request (user_id, request_id);

-- Recover original prepayments from the ledger; current model prices may already have changed.
UPDATE generation_task task
LEFT JOIN sys_ai_model model ON model.id = task.model_id
LEFT JOIN (SELECT user_id, biz_no, -SUM(change_value) AS deducted
           FROM bean_transaction WHERE type = 'DEDUCT' AND biz_no LIKE 'img:%'
           GROUP BY user_id, biz_no) debit ON debit.user_id = task.user_id AND debit.biz_no = CONCAT('img:', task.task_id)
SET task.model_name_snapshot = model.model_name,
    task.provider_snapshot = model.provider,
    task.base_url_snapshot = model.base_url,
    task.model_deployment_snapshot = model.model_deployment,
    task.image_quality_snapshot = model.image_quality,
    task.bean_per_image_snapshot = COALESCE(debit.deducted, 0) DIV GREATEST(COALESCE(task.n, 1), 1),
    task.prepaid_beans = COALESCE(debit.deducted, 0)
WHERE task.bean_cost IS NULL;

-- Reuse the owner's existing prices when available. Credentials and activation require validation.
INSERT INTO sys_ai_model
    (model_name, api_key, base_url, model_deployment, provider, model_type, api_protocol,
     supported_resolutions, supported_aspect_ratios, default_resolution, image_quality,
     bean_per_image, bean_per_image_1k, bean_per_image_2k, bean_per_image_4k,
     is_active, is_default, sort_order, create_time, update_time)
SELECT 'GPT Image 2.5 Flare (Luxai)', '', 'https://api.openlux.ai/v1', 'gpt-image-2.5-flare',
       'luxai', 'image', 'chat_completions', '1K,2K,4K', '1:1,16:9,9:16,3:2,2:3,4:3,3:4',
       '1K', 'auto', src.bean_per_image, src.bean_per_image_1k, src.bean_per_image_2k, src.bean_per_image_4k,
       0, 0, 10, NOW(), NOW()
FROM (SELECT 1 AS seed) seed
LEFT JOIN (SELECT bean_per_image, bean_per_image_1k, bean_per_image_2k, bean_per_image_4k
           FROM sys_ai_model WHERE model_type = 'image' AND is_default = 1 AND provider <> 'luxai'
           ORDER BY is_active DESC, sort_order ASC, id ASC LIMIT 1) src ON 1 = 1
WHERE NOT EXISTS (SELECT 1 FROM sys_ai_model WHERE provider = 'luxai' AND model_deployment = 'gpt-image-2.5-flare');

INSERT INTO sys_ai_model
    (model_name, api_key, base_url, model_deployment, provider, model_type, api_protocol,
     supported_resolutions, supported_aspect_ratios, default_resolution, image_quality,
     bean_per_image, bean_per_image_1k, bean_per_image_2k, bean_per_image_4k,
     is_active, is_default, sort_order, create_time, update_time)
SELECT 'GPT Image 2.5 Sunburst (Luxai)', '', 'https://api.openlux.ai/v1', 'gpt-image-2.5-sunburst',
       'luxai', 'image', 'chat_completions', '1K,2K,4K', '1:1,16:9,9:16,3:2,2:3,4:3,3:4',
       '1K', 'auto', src.bean_per_image, src.bean_per_image_1k, src.bean_per_image_2k, src.bean_per_image_4k,
       0, 0, 11, NOW(), NOW()
FROM (SELECT 1 AS seed) seed
LEFT JOIN (SELECT bean_per_image, bean_per_image_1k, bean_per_image_2k, bean_per_image_4k
           FROM sys_ai_model WHERE model_type = 'image' AND is_default = 1 AND provider <> 'luxai'
           ORDER BY is_active DESC, sort_order ASC, id ASC LIMIT 1) src ON 1 = 1
WHERE NOT EXISTS (SELECT 1 FROM sys_ai_model WHERE provider = 'luxai' AND model_deployment = 'gpt-image-2.5-sunburst');

INSERT INTO sys_ai_model
    (model_name, api_key, base_url, model_deployment, provider, model_type, api_protocol,
     temperature, max_tokens, bean_per_mtoken_input, bean_per_mtoken_input_cached, bean_per_mtoken_output,
     is_active, is_default, sort_order, create_time, update_time)
SELECT 'GPT-6.1 Sol (Luxai)', '', 'https://api.openlux.ai/v1', 'gpt-6.1-sol', 'luxai', 'chat',
       'responses', 0.7, 8192, src.bean_per_mtoken_input, src.bean_per_mtoken_input_cached, src.bean_per_mtoken_output,
       0, 0, -20, NOW(), NOW()
FROM (SELECT 1 AS seed) seed
LEFT JOIN (SELECT bean_per_mtoken_input, bean_per_mtoken_input_cached, bean_per_mtoken_output
           FROM sys_ai_model WHERE model_type = 'chat' AND model_deployment = 'gpt-5.6-sol'
           ORDER BY is_default DESC, is_active DESC, sort_order ASC, id ASC LIMIT 1) src ON 1 = 1
WHERE NOT EXISTS (SELECT 1 FROM sys_ai_model WHERE provider = 'luxai' AND model_deployment = 'gpt-6.1-sol');
