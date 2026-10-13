-- 迁移:会员等级 · 会员时长字段(支持按等级配置购买后开通的天数)
-- 日期:2026-06-30
-- 说明:
--   membership_level 新增 duration_days,表示购买该等级后的会员有效天数;
--   默认 0 兼容存量配置(开通逻辑读取本字段计算到期时间)。
-- Flyway:本目录受 Flyway 管控,启动时按版本顺序自动执行,仅写增量。

ALTER TABLE `membership_level`
    ADD COLUMN `duration_days` INT NOT NULL DEFAULT 0 COMMENT '会员时长(天)';
