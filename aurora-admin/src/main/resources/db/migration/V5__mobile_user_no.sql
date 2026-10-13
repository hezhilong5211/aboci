-- 迁移：移动端用户编号 user_no
-- 日期：2026-06-22
-- 说明：mobile_user 增加 user_no 列并加唯一索引;对存量行用 create_time 回填一个近似编号。
--      编号格式 = 'Ts' + yyMMddHH(注册时刻) + 4位随机[a-zA-Z0-9] + 4位随机数字(0001-9999)。
--      回填为 legacy 兜底口径:随机段用 SQL 近似(CONV 取 base36 4 位 + LPAD 随机数 4 位;
--      避开 MySQL 9.x 不提供的 MD5 等函数),
--      日期段取 create_time;create_time 为空时退化用 NOW()。新用户的标准编号由后端 UserNoGenerator 生成。
-- Flyway:本目录受 Flyway 管控,启动时按版本顺序自动执行,仅写增量,不动存量结构外的数据语义。

-- ===== 增加 user_no 列 =====
ALTER TABLE `mobile_user`
    ADD COLUMN `user_no` VARCHAR(32) DEFAULT NULL COMMENT '用户编号(Ts+yyMMddHH+4随机+4数字)' AFTER `user_id`;

-- ===== 回填存量行(当前仅 1 条测试用户;按主键逐行生成可避免随机段碰撞)=====
UPDATE `mobile_user`
SET `user_no` = CONCAT(
        'Ts',
        DATE_FORMAT(COALESCE(`create_time`, NOW()), '%y%m%d%H'),
        LPAD(CONV(FLOOR(RAND() * 1679616), 10, 36), 4, '0'),
        LPAD(FLOOR(1 + RAND() * 9999), 4, '0')
    )
WHERE `user_no` IS NULL;

-- ===== 加唯一索引 =====
ALTER TABLE `mobile_user`
    ADD UNIQUE KEY `uk_user_no` (`user_no`);
