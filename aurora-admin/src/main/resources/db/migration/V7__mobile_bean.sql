-- 迁移：移动端二期 · 豆子账户地基(账户表 + 流水表 + 配置 + 菜单权限)
-- 日期：2026-06-23
-- 说明：
--   bean_account：每个移动端用户一条豆子账户,user_id 唯一索引;balance/total_earned/total_consumed 默认 0。
--   bean_transaction：账户每次变动记一条流水,balance_after 为变动后余额快照;索引 (user_id, create_time)。
--   字符集统一 utf8mb4_0900_ai_ci。豆子余额唯一写入路径 = 账户服务,流水与余额同事务写入。
--   sys_config seed 两行:mobile.bean.new_user_gift=50(新用户赠送豆数)、mobile.bean.warn_threshold=20(余额预警阈值)。
--     config_id 不显式指定,交由自增分配,避免与既有数据冲突;按现有 config_key/config_value 结构插入。
--   sys_menu:在「移动端管理」目录(menu_id=2200)下新增豆子账户两个按钮权限。
--     menu_id 从 2212 起,避开 V3(2200-2208)与 V6(2209-2211)已占用区间。
--     权限串与后端 @ss.hasPermi 对齐:mobile:bean:list / mobile:bean:adjust。
-- Flyway:本目录受 Flyway 管控,启动时按版本顺序自动执行,仅写增量。

-- ===== 豆子账户表 =====
CREATE TABLE IF NOT EXISTS `bean_account` (
    `account_id`     BIGINT   NOT NULL AUTO_INCREMENT COMMENT '账户ID',
    `user_id`        BIGINT   NOT NULL COMMENT '用户ID',
    `balance`        BIGINT   NOT NULL DEFAULT 0 COMMENT '当前余额(≥0)',
    `total_earned`   BIGINT   NOT NULL DEFAULT 0 COMMENT '累计获得豆子',
    `total_consumed` BIGINT   NOT NULL DEFAULT 0 COMMENT '累计消耗豆子',
    `create_time`    DATETIME DEFAULT NULL COMMENT '创建时间',
    `update_time`    DATETIME DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`account_id`),
    UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='移动端豆子账户表';

-- ===== 豆子流水表 =====
CREATE TABLE IF NOT EXISTS `bean_transaction` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`       BIGINT       NOT NULL COMMENT '用户ID',
    `type`          VARCHAR(16)  NOT NULL COMMENT '类型(GIFT赠送/ISSUE发放/RECHARGE充值/DEDUCT扣费/REFUND退还/ADJUST后台调整)',
    `change_value`  BIGINT       NOT NULL COMMENT '变动值(正负)',
    `balance_after` BIGINT       NOT NULL COMMENT '变动后余额快照',
    `biz_no`        VARCHAR(64)  DEFAULT NULL COMMENT '关联业务单号(可空)',
    `remark`        VARCHAR(255) DEFAULT NULL COMMENT '备注',
    `create_time`   DATETIME     DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_create` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='移动端豆子流水表';

-- ===== sys_config 种子(config_id 自增,避免与既有冲突)=====
INSERT INTO `sys_config`
    (`config_name`, `config_key`, `config_value`, `config_type`,
     `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES
    ('新用户赠送豆数', 'mobile.bean.new_user_gift', '50', 'N',
     'admin', NOW(), '', NULL, '移动端新用户注册赠送的豆子数量'),
    ('豆子余额预警阈值', 'mobile.bean.warn_threshold', '20', 'N',
     'admin', NOW(), '', NULL, '移动端豆子余额低于该值时预警');

-- ===== 菜单:豆子账户 按钮权限(挂在移动端管理目录 2200 下)=====
-- sys_menu 字段顺序与 V3/V6 保持一致:
--   menu_id, menu_name, parent_id, order_num, path, component, query,
--   is_frame, is_cache, menu_type, visible, status, perms, icon,
--   create_by, create_time, update_by, update_time, remark, del_flag
INSERT INTO `sys_menu`
    (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`,
     `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`,
     `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`)
VALUES
    (2212, '豆子流水查询', 2200, 4, '', NULL, NULL, 1, 0, 'F', '0', '0', 'mobile:bean:list', '#',
     'admin', NOW(), '', NULL, '查看移动端用户豆子流水', '0'),
    (2213, '豆子手动调整', 2200, 5, '', NULL, NULL, 1, 0, 'F', '0', '0', 'mobile:bean:adjust', '#',
     'admin', NOW(), '', NULL, '后台手动增减用户豆子', '0');
