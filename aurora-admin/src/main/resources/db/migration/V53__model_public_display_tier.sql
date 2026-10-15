-- Internal model names, deployment IDs and task snapshots stay unchanged.
ALTER TABLE sys_ai_model
    ADD COLUMN display_tier VARCHAR(8) NOT NULL DEFAULT '中' COMMENT '小程序公开档位：轻、中、高';

-- Bind public labels to model records, independently of price, list order or default status.
UPDATE sys_ai_model
SET display_tier = CASE
    WHEN model_type = 'chat' AND LOWER(model_deployment) = 'gpt-6.1-sol' THEN '高'
    WHEN model_type = 'image' AND LOWER(model_deployment) = 'gpt-image-2.5-sunburst' THEN '高'
    WHEN model_type = 'image' AND LOWER(model_deployment) = 'gpt-image-2.5-flare' THEN '中'
    WHEN model_type = 'image' THEN '轻'
    ELSE '中'
END
WHERE model_type IN ('chat', 'image');
