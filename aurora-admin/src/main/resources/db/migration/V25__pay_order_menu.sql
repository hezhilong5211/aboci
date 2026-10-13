-- 迁移:订单管理后台菜单(无新建表,仅菜单权限)
-- 日期:2026-06-30
-- 说明:
--   sys_menu:在「移动端管理」目录(menu_id=2200)下新增可见菜单「订单管理」+ 查询/详情两个按钮权限,
--     menu_id 用 2248-2250(避开 ≤2247 已占用区间,其中 2243-2247 由 V23 占用)。
--     权限串与后端 @ss.hasPermi 对齐:mobile:order:list(列表/查询)、mobile:order:query(详情)。
--     订单为业务流水,后台仅提供只读查询(列表 + 详情),不建增删改按钮。
-- Flyway:本目录受 Flyway 管控,启动时按版本顺序自动执行,仅写增量。

-- ===== 菜单:订单管理(可见菜单 + 按钮权限,挂在移动端管理目录 2200 下,menu_id 从 2248 起)=====
-- sys_menu 字段顺序与 V3/V6/V7/V8/V9/V23 保持一致:
--   menu_id, menu_name, parent_id, order_num, path, component, query,
--   is_frame, is_cache, menu_type, visible, status, perms, icon,
--   create_by, create_time, update_by, update_time, remark, del_flag
INSERT INTO `sys_menu`
    (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`,
     `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`,
     `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`)
VALUES
    -- 1. 菜单:订单管理(可见,component=mobile/pay-order/index)
    (2248, '订单管理', 2200, 7, 'pay-order', 'mobile/pay-order/index', NULL, 1, 0, 'C', '0', '0', 'mobile:order:list', 'icon-file',
     'admin', NOW(), '', NULL, '支付订单查询', '0'),
    -- 2. 订单管理 按钮权限
    (2249, '订单查询', 2248, 1, '', NULL, NULL, 1, 0, 'F', '0', '0', 'mobile:order:list', '#',
     'admin', NOW(), '', NULL, '', '0'),
    (2250, '订单详情', 2248, 2, '', NULL, NULL, 1, 0, 'F', '0', '0', 'mobile:order:query', '#',
     'admin', NOW(), '', NULL, '', '0');
