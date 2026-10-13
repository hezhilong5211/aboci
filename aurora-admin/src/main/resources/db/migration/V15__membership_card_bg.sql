-- 迁移:移动端二期 · 会员等级卡片背景图
-- 日期:2026-06-25
-- 说明:
--   membership_level 增列 card_bg(会员卡片背景图URL),供 C 端会员卡片展示。
--   可空:存量等级无背景图时为 NULL;C 端按当前段 level_id 取该列(等级已删则为 null)。
-- 约束:MySQL 9.6 已移除 MD5() 等函数,本脚本不使用;Flyway 仅写增量,禁 DROP/TRUNCATE。

ALTER TABLE `membership_level`
    ADD COLUMN `card_bg` VARCHAR(255) NULL COMMENT '会员卡片背景图URL' AFTER `status`;
