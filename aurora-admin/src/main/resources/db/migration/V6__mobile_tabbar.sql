-- 迁移：移动端底部导航(tabbar)后台配置表 + 菜单权限
-- 日期：2026-06-22
-- 说明：
--   新建 sys_mobile_tabbar 表,承载移动端 4 个固定 Tab 的可配置项(名称/双态 icon/排序/显隐)。
--   tab_key、page_path 由前端固定、后台只读不可改;后台只编辑 tab_name/icon_normal/icon_active/visible/sort。
--   字符集统一 utf8mb4_0900_ai_ci;审计五字段(create_by/create_time/update_by/update_time/remark)+ 逻辑删 del_flag(0存在/2删除)。
--   同时 seed 4 条(icon 先留空,后台上传后回填):chat/对话、create/生成、cases/案例、profile/我的。
--   配套 sys_menu:在「移动端管理」目录(menu_id=2200)下新增可见菜单「底部导航」+ 查询/编辑两个按钮权限。
--     menu_id 从 2209 起,避开 V3 已占用的 2200-2208。
--     权限串与后端 @ss.hasPermi 对齐:mobile:tabbar:list / mobile:tabbar:edit。
-- Flyway:本目录受 Flyway 管控,启动时按版本顺序自动执行(基线 V1,本脚本 V6 增量),仅写增量。

-- ===== 移动端底部导航配置表 =====
CREATE TABLE IF NOT EXISTS `sys_mobile_tabbar` (
    `tab_id`      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `tab_key`     VARCHAR(32)  NOT NULL COMMENT 'Tab 唯一标识(前端固定,只读)',
    `tab_name`    VARCHAR(50)  DEFAULT NULL COMMENT 'Tab 名称',
    `icon_normal` VARCHAR(500) DEFAULT NULL COMMENT '普通态图标URL',
    `icon_active` VARCHAR(500) DEFAULT NULL COMMENT '选中态图标URL',
    `page_path`   VARCHAR(200) DEFAULT NULL COMMENT '页面路径(前端固定,只读)',
    `sort`        INT          DEFAULT 0 COMMENT '排序号(升序)',
    `visible`     TINYINT      DEFAULT 1 COMMENT '是否显示(1显示/0隐藏)',
    `create_by`   VARCHAR(64)  DEFAULT NULL COMMENT '创建者',
    `create_time` DATETIME     DEFAULT NULL COMMENT '创建时间',
    `update_by`   VARCHAR(64)  DEFAULT NULL COMMENT '更新者',
    `update_time` DATETIME     DEFAULT NULL COMMENT '更新时间',
    `remark`      VARCHAR(500) DEFAULT NULL COMMENT '备注',
    `del_flag`    CHAR(1)      DEFAULT '0' COMMENT '逻辑删除标记(0存在/2删除)',
    PRIMARY KEY (`tab_id`),
    UNIQUE KEY `uk_tab_key` (`tab_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='移动端底部导航配置表';

-- ===== seed 4 个固定 Tab(icon 先留空,后台上传后回填)=====
INSERT INTO `sys_mobile_tabbar`
    (`tab_key`, `tab_name`, `icon_normal`, `icon_active`, `page_path`, `sort`, `visible`,
     `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`)
VALUES
    ('chat',    '对话', '', '', '/pages/index/index',  0, 1, 'admin', NOW(), '', NULL, NULL, '0'),
    ('create',  '生成', '', '', '/pages/create/index', 1, 1, 'admin', NOW(), '', NULL, NULL, '0'),
    ('cases',   '案例', '', '', '/pages/cases/index',  2, 1, 'admin', NOW(), '', NULL, NULL, '0'),
    ('profile', '我的', '', '', '/pages/user/index',   3, 1, 'admin', NOW(), '', NULL, NULL, '0');

-- ===== 菜单:底部导航(可见菜单,挂在移动端管理目录 2200 下)=====
-- sys_menu 字段顺序与 V3 保持一致:
--   menu_id, menu_name, parent_id, order_num, path, component, query,
--   is_frame, is_cache, menu_type, visible, status, perms, icon,
--   create_by, create_time, update_by, update_time, remark, del_flag
INSERT INTO `sys_menu`
    (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`,
     `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`,
     `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`)
VALUES
    (2209, '底部导航', 2200, 3, 'tabbar', 'mobile/tabbar/index', NULL, 1, 0, 'C', '0', '0', 'mobile:tabbar:list', 'icon-menu',
     'admin', NOW(), '', NULL, '移动端底部导航配置(名称/双态图标/显隐)', '0');

-- ===== 底部导航 按钮权限 =====
INSERT INTO `sys_menu`
    (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`,
     `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`,
     `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`)
VALUES
    (2210, '底部导航查询', 2209, 1, '', NULL, NULL, 1, 0, 'F', '0', '0', 'mobile:tabbar:list', '#',
     'admin', NOW(), '', NULL, '', '0'),
    (2211, '底部导航编辑', 2209, 2, '', NULL, NULL, 1, 0, 'F', '0', '0', 'mobile:tabbar:edit', '#',
     'admin', NOW(), '', NULL, '', '0');
