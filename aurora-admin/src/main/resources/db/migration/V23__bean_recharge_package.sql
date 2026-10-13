-- 迁移:豆子充值套餐(套餐配置表 + 后台菜单权限)
-- 日期:2026-06-30
-- 说明:
--   bean_recharge_package:豆子充值套餐配置(到账豆数 / 价格 / 排序 / 上下架),逻辑删 del_flag(0存在/2删除)。
--     beans 为充值到账豆数(BIGINT);price 价格单位为元(DECIMAL(10,2));status 0上架/1下架。
--     字符集统一 utf8mb4_0900_ai_ci;带审计五字段。
--   sys_menu:在「移动端管理」目录(menu_id=2200)下新增可见菜单「豆子套餐」+ 四个按钮权限,
--     menu_id 从 2243 起(避开 ≤2242 已占用区间)。
--     权限串与后端 @ss.hasPermi 对齐:mobile:beanpackage:list/add/edit/remove。
-- Flyway:本目录受 Flyway 管控,启动时按版本顺序自动执行,仅写增量。

-- ===== 豆子充值套餐表 =====
CREATE TABLE IF NOT EXISTS `bean_recharge_package` (
    `package_id`  BIGINT        NOT NULL AUTO_INCREMENT COMMENT '套餐ID',
    `beans`       BIGINT        NOT NULL DEFAULT 0 COMMENT '到账豆数',
    `price`       DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '价格(元)',
    `sort`        INT           NOT NULL DEFAULT 0 COMMENT '排序(小在前)',
    `status`      CHAR(1)       NOT NULL DEFAULT '0' COMMENT '状态(0上架/1下架)',
    `create_by`   VARCHAR(64)   DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME      DEFAULT NULL COMMENT '创建时间',
    `update_by`   VARCHAR(64)   DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME      DEFAULT NULL COMMENT '更新时间',
    `remark`      VARCHAR(255)  DEFAULT NULL COMMENT '备注',
    `del_flag`    CHAR(1)       NOT NULL DEFAULT '0' COMMENT '逻辑删除标记(0存在/2删除)',
    PRIMARY KEY (`package_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='移动端豆子充值套餐表';

-- ===== 菜单:豆子套餐(可见菜单 + 按钮权限,挂在移动端管理目录 2200 下,menu_id 从 2243 起)=====
-- sys_menu 字段顺序与 V3/V6/V7/V8/V9 保持一致:
--   menu_id, menu_name, parent_id, order_num, path, component, query,
--   is_frame, is_cache, menu_type, visible, status, perms, icon,
--   create_by, create_time, update_by, update_time, remark, del_flag
INSERT INTO `sys_menu`
    (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`,
     `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`,
     `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`)
VALUES
    -- 1. 菜单:豆子套餐(可见,component=mobile/bean-package/index)
    (2243, '豆子套餐', 2200, 6, 'bean-package', 'mobile/bean-package/index', NULL, 1, 0, 'C', '0', '0', 'mobile:beanpackage:list', 'icon-gift',
     'admin', NOW(), '', NULL, '豆子充值套餐管理', '0'),
    -- 2. 豆子套餐 按钮权限
    (2244, '豆子套餐查询', 2243, 1, '', NULL, NULL, 1, 0, 'F', '0', '0', 'mobile:beanpackage:list', '#',
     'admin', NOW(), '', NULL, '', '0'),
    (2245, '豆子套餐新增', 2243, 2, '', NULL, NULL, 1, 0, 'F', '0', '0', 'mobile:beanpackage:add', '#',
     'admin', NOW(), '', NULL, '', '0'),
    (2246, '豆子套餐修改', 2243, 3, '', NULL, NULL, 1, 0, 'F', '0', '0', 'mobile:beanpackage:edit', '#',
     'admin', NOW(), '', NULL, '', '0'),
    (2247, '豆子套餐删除', 2243, 4, '', NULL, NULL, 1, 0, 'F', '0', '0', 'mobile:beanpackage:remove', '#',
     'admin', NOW(), '', NULL, '', '0');
