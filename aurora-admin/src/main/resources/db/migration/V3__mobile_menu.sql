-- =============================================================================
-- 迁移脚本:移动端管理后台菜单与权限
-- 日期:2026-06-21
-- 用途:
--   为「移动端管理」一页三卡(移动端用户列表 + 小程序配置 + 微信支付配置)
--   注册后台目录/菜单/按钮权限,使角色可被授权访问。
-- 说明:
--   - 本项目 sys_menu 主键 menu_id,现有最大值 2127(AUTO_INCREMENT=2128)。
--     为留足安全间隔,新数据从 2200 起,避开冲突。
--   - 页面为「一页三卡」,实际只有一个前端页面 mobile/index。
--     因此「移动端用户」(2201)为可见承载菜单(component=mobile/index),
--     「微信配置」(2204)为隐藏菜单(visible=1,同一页面),仅用于归类其权限按钮,
--     避免出现两个指向同一页面的可点入口。
--   - 权限标识与后端 @ss.hasPermi 完全对齐:
--       mobile:user:list / mobile:user:edit
--       system:wxconfig:list / add / edit / remove
--   - sys_menu 字段顺序:
--       menu_id, menu_name, parent_id, order_num, path, component, query,
--       is_frame, is_cache, menu_type, visible, status, perms, icon,
--       create_by, create_time, update_by, update_time, remark, del_flag
--   - 仅生成脚本,不在此自动执行,请在汇聚阶段统一对库执行。
-- =============================================================================

-- 1. 目录:移动端管理(顶级目录 M)
INSERT INTO `sys_menu`
    (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`,
     `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`,
     `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`)
VALUES
    (2200, '移动端管理', 0, 50, 'mobile', NULL, NULL, 1, 0, 'M', '0', '0', NULL, 'icon-mobile',
     'admin', NOW(), '', NULL, '移动端管理目录', '0');

-- 2. 菜单:移动端用户(可见承载页,component=mobile/index)
INSERT INTO `sys_menu`
    (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`,
     `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`,
     `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`)
VALUES
    (2201, '移动端用户', 2200, 1, 'index', 'mobile/index', NULL, 1, 0, 'C', '0', '0', 'mobile:user:list', 'icon-user',
     'admin', NOW(), '', NULL, '移动端用户/微信配置一页三卡', '0');

-- 3. 移动端用户 按钮权限(list 已挂在菜单上,这里补 edit)
INSERT INTO `sys_menu`
    (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`,
     `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`,
     `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`)
VALUES
    (2202, '移动端用户查询', 2201, 1, '', NULL, NULL, 1, 0, 'F', '0', '0', 'mobile:user:list', '#',
     'admin', NOW(), '', NULL, '', '0'),
    (2203, '移动端用户启禁用', 2201, 2, '', NULL, NULL, 1, 0, 'F', '0', '0', 'mobile:user:edit', '#',
     'admin', NOW(), '', NULL, '', '0');

-- 4. 菜单:微信配置(隐藏菜单,同一页面,仅用于归类微信配置权限按钮)
INSERT INTO `sys_menu`
    (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`,
     `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`,
     `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`)
VALUES
    (2204, '微信配置', 2200, 2, 'wx-config', 'mobile/index', NULL, 1, 0, 'C', '1', '0', 'system:wxconfig:list', 'icon-settings',
     'admin', NOW(), '', NULL, '小程序/支付配置(隐藏,合入移动端管理页)', '0');

-- 5. 微信配置 按钮权限
INSERT INTO `sys_menu`
    (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`,
     `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`,
     `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`)
VALUES
    (2205, '微信配置查询', 2204, 1, '', NULL, NULL, 1, 0, 'F', '0', '0', 'system:wxconfig:list', '#',
     'admin', NOW(), '', NULL, '', '0'),
    (2206, '微信配置新增', 2204, 2, '', NULL, NULL, 1, 0, 'F', '0', '0', 'system:wxconfig:add', '#',
     'admin', NOW(), '', NULL, '', '0'),
    (2207, '微信配置修改', 2204, 3, '', NULL, NULL, 1, 0, 'F', '0', '0', 'system:wxconfig:edit', '#',
     'admin', NOW(), '', NULL, '', '0'),
    (2208, '微信配置删除', 2204, 4, '', NULL, NULL, 1, 0, 'F', '0', '0', 'system:wxconfig:remove', '#',
     'admin', NOW(), '', NULL, '', '0');
