-- 案例/模板:本周精选标记
-- template 增加 is_featured:1=本周精选(后台从案例列表选,最多 5 个),0=否;
-- C 端案例页顶部轮播取 is_featured=1 且上架的(最多 5);若无精选则兜底取最早添加的 5 张。
-- 约束:Flyway 仅写增量,禁 DROP/TRUNCATE;字段可空默认 0,存量行不受影响。
ALTER TABLE `template`
    ADD COLUMN `is_featured` TINYINT(1) DEFAULT 0 COMMENT '本周精选(1是/0否,后台选最多5个)' AFTER `sort`;
