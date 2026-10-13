-- 迁移:V13 — 计费单价改为整数
-- 日期:2026-06-24
-- 说明:豆子单价无需小数,sys_ai_model 的 5 个计费列由 DECIMAL(12,4) 改为 BIGINT(整数豆子/单位)。
--      扣费时仍按「用量/百万token × 单价」折算并向上取整(ceil),故单价整数不影响计费精度。
-- 约束:MySQL 9.6 禁 MD5;Flyway 仅增量,禁 DROP/TRUNCATE;V12 已执行不可改,故以 V13 ALTER 修正类型。
--      原 DECIMAL 存量值均为整数(无小数),MODIFY 为 BIGINT 无损。

ALTER TABLE sys_ai_model
    MODIFY COLUMN bean_per_mtoken_input        BIGINT NULL COMMENT '文本-每百万token输入豆子单价(缓存未命中)',
    MODIFY COLUMN bean_per_mtoken_input_cached BIGINT NULL COMMENT '文本-每百万token输入豆子单价(缓存命中,仅 deepseek 填)',
    MODIFY COLUMN bean_per_mtoken_output       BIGINT NULL COMMENT '文本-每百万token输出豆子单价',
    MODIFY COLUMN bean_per_image               BIGINT NULL COMMENT '图片-每张豆子单价',
    MODIFY COLUMN bean_per_second              BIGINT NULL COMMENT '视频-每秒豆子单价(本期仅配置不实扣)';
