-- 手工运维配置，不是 Flyway 迁移；执行前备份，暂停新任务并等待已有任务结束。
-- 仅针对本次线上三个可用图片模型；ID/档位或可用分辨率不匹配时事务中止。
-- 使用 mysql 客户端执行，不加 --force；遇错退出后连接关闭，未提交事务自动回滚。
SET NAMES utf8mb4;
SET SESSION sql_mode = 'STRICT_ALL_TABLES,NO_ENGINE_SUBSTITUTION';
CREATE TEMPORARY TABLE image_tier_pricing_guard (ok TINYINT NOT NULL);
START TRANSACTION;
SELECT id FROM sys_ai_model WHERE id IN (14, 17, 18) ORDER BY id FOR UPDATE;

INSERT INTO image_tier_pricing_guard (ok)
SELECT IF(COUNT(*) = 3, 1, NULL)
FROM sys_ai_model
WHERE model_type = 'image' AND is_active = 1
  AND supported_resolutions = '1K,2K,4K' AND default_resolution = '1K'
  AND ((id = 14 AND display_tier = '轻')
    OR (id = 17 AND display_tier = '中')
    OR (id = 18 AND display_tier = '高'));

UPDATE sys_ai_model
SET bean_per_image = CASE display_tier WHEN '轻' THEN 6 WHEN '中' THEN 10 WHEN '高' THEN 15 END,
    bean_per_image_1k = CASE display_tier WHEN '轻' THEN 6 WHEN '中' THEN 10 WHEN '高' THEN 15 END,
    bean_per_image_2k = CASE display_tier WHEN '轻' THEN 8 WHEN '中' THEN 15 WHEN '高' THEN 20 END,
    bean_per_image_4k = CASE display_tier WHEN '轻' THEN 10 WHEN '中' THEN 20 WHEN '高' THEN 30 END,
    update_time = CURRENT_TIMESTAMP
WHERE id IN (14, 17, 18) AND model_type = 'image';

INSERT INTO image_tier_pricing_guard (ok)
SELECT IF(COUNT(*) = 3, 1, NULL)
FROM sys_ai_model
WHERE bean_per_image = bean_per_image_1k
  AND ((id = 14 AND bean_per_image_1k = 6 AND bean_per_image_2k = 8 AND bean_per_image_4k = 10)
    OR (id = 17 AND bean_per_image_1k = 10 AND bean_per_image_2k = 15 AND bean_per_image_4k = 20)
    OR (id = 18 AND bean_per_image_1k = 15 AND bean_per_image_2k = 20 AND bean_per_image_4k = 30));
COMMIT;
DROP TEMPORARY TABLE image_tier_pricing_guard;
