-- AI 模型「默认」标志
-- 为 sys_ai_model 增加 is_default:同一 model_type 下唯一(由后台「设为默认」接口保证),
-- 出图选型(对话内 generate_image 与生成页 /generate 共用)优先取 is_default=1 的图像模型,无默认则按 sort_order。
-- 约束:Flyway 仅写增量,禁 DROP/TRUNCATE;字段可空默认 0,存量行不受影响。
ALTER TABLE `sys_ai_model`
    ADD COLUMN `is_default` TINYINT(1) DEFAULT 0 COMMENT '是否默认(同 model_type 唯一,1默认/0否)' AFTER `is_active`;
