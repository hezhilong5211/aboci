-- 迁移:移动端二期 · 模型计费与扣豆(sys_ai_model 计费字段)
-- 日期:2026-06-24
-- 说明:
--   按 PRD「PRD-模型计费与扣豆.md」§三,为 sys_ai_model 增加 5 列豆子单价,按 model_type 显隐录入:
--     chat/vision(文本,按每百万 token):bean_per_mtoken_input(输入/缓存未命中)、
--       bean_per_mtoken_input_cached(输入·缓存命中,仅 deepseek 填)、bean_per_mtoken_output(输出);
--     image(图片,按张):bean_per_image;
--     video(视频,按秒,本期仅配置不实扣):bean_per_second。
--   单价用 DECIMAL(12,4) 支持小数,默认 NULL(未配置);算费时按 token/张/秒累加后 ceil 取整,豆子为整数。
-- 约束:MySQL 9.6 已移除 MD5() 等函数,本脚本不使用;Flyway 仅写增量,禁 DROP/TRUNCATE。

ALTER TABLE sys_ai_model ADD COLUMN bean_per_mtoken_input DECIMAL(12,4) DEFAULT NULL COMMENT '文本-每百万token输入豆子单价(缓存未命中)' AFTER default_resolution;
ALTER TABLE sys_ai_model ADD COLUMN bean_per_mtoken_input_cached DECIMAL(12,4) DEFAULT NULL COMMENT '文本-每百万token输入豆子单价(缓存命中,仅deepseek填)' AFTER bean_per_mtoken_input;
ALTER TABLE sys_ai_model ADD COLUMN bean_per_mtoken_output DECIMAL(12,4) DEFAULT NULL COMMENT '文本-每百万token输出豆子单价' AFTER bean_per_mtoken_input_cached;
ALTER TABLE sys_ai_model ADD COLUMN bean_per_image DECIMAL(12,4) DEFAULT NULL COMMENT '图片-每张豆子单价' AFTER bean_per_mtoken_output;
ALTER TABLE sys_ai_model ADD COLUMN bean_per_second DECIMAL(12,4) DEFAULT NULL COMMENT '视频-每秒豆子单价(本期仅配置不实扣)' AFTER bean_per_image;
