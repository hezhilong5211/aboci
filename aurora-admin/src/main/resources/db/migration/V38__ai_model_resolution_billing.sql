-- 图片模型按分辨率分别配置每张豆子单价；旧统一单价保留为兼容兜底。
ALTER TABLE sys_ai_model ADD COLUMN bean_per_image_1k DECIMAL(12,4) DEFAULT NULL COMMENT '图片-1K每张豆子单价' AFTER bean_per_image;
ALTER TABLE sys_ai_model ADD COLUMN bean_per_image_2k DECIMAL(12,4) DEFAULT NULL COMMENT '图片-2K每张豆子单价' AFTER bean_per_image_1k;
ALTER TABLE sys_ai_model ADD COLUMN bean_per_image_4k DECIMAL(12,4) DEFAULT NULL COMMENT '图片-4K每张豆子单价' AFTER bean_per_image_2k;

-- 已有图片模型先沿用原统一单价，管理员可再分别调整三档价格。
UPDATE sys_ai_model
SET bean_per_image_1k = bean_per_image,
    bean_per_image_2k = bean_per_image,
    bean_per_image_4k = bean_per_image
WHERE model_type = 'image';
