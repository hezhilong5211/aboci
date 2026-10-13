-- =====================================================================
-- V20:治理 sys_menu —— 知识库菜单三套去重 + 图标按钮去乱码归类
-- 背景:历史导入致「知识库管理」菜单树重复三套并夹带乱码/英文占位:
--   · 2024 树(挂「模型配置」2000):菜单名正常,但子按钮为英文占位,file:list/remove 乱码;
--   · 2032 树(子 2033-2038):中文正常,但父菜单 2032 不存在 → 整组孤儿(菜单中不可见);
--   · 2040 树(挂「知识角色」2056):中文全部正常 → 保留此套。
--   另有 5 个图标按钮(system:icon:*,2117-2121)乱码且直挂「系统管理」目录、无归属菜单。
-- 处理:保留干净的 2040 树;把后端仍在用的 file:list/remove(SysKnowledgeFileController)
--   迁入 2040 并修中文名;删除重复的 2024 树、孤儿 2032 组,及删空后的「模型配置」目录;
--   图标 5 按钮修中文名并归入新建「图标管理」菜单。
-- 安全:上述待删/待改菜单经核均无 sys_role_menu 引用;按 menu_id 操作,不存在即空操作。
-- 注:图标无独立页面,system:icon 由 IconPicker 组件(菜单/tabbar 管理内)调用;
--   「图标管理」菜单 component 为约定占位路径,页面建成前仅作权限归类节点。
-- Flyway:本目录受 Flyway 管控,启动时按版本顺序自动执行,仅写增量。
-- =====================================================================

-- 1. 迁移仍在用的文件权限到保留的 2040 树,并修中文名(2126 列表 / 2127 删除)
UPDATE `sys_menu` SET `parent_id` = 2040, `menu_name` = '文件列表' WHERE `menu_id` = 2126;
UPDATE `sys_menu` SET `parent_id` = 2040, `menu_name` = '文件删除' WHERE `menu_id` = 2127;

-- 2. 清除重复/孤儿知识库菜单(先清可能的角色关联,再删菜单)
DELETE FROM `sys_role_menu` WHERE `menu_id` IN
    (2000, 2024, 2025, 2026, 2027, 2028, 2029, 2030, 2031, 2039,
     2033, 2034, 2035, 2036, 2037, 2038);
-- 2024 树(英文占位,权限 2040 树已具备)
DELETE FROM `sys_menu` WHERE `menu_id` IN (2024, 2025, 2026, 2027, 2028, 2029, 2030, 2031, 2039);
-- 2032 孤儿组(父菜单 2032 不存在,菜单中本不可见)
DELETE FROM `sys_menu` WHERE `menu_id` IN (2033, 2034, 2035, 2036, 2037, 2038);
-- 「模型配置」目录(2000)其下两菜单已删空,移除空目录
DELETE FROM `sys_menu` WHERE `menu_id` = 2000;

-- 3. 新建「图标管理」菜单(C)作图标权限归类节点
DELETE FROM `sys_menu` WHERE `menu_id` = 2242;
INSERT INTO `sys_menu`
    (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`,
     `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`,
     `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`)
VALUES
    (2242, '图标管理', 1, 10, 'icon', 'system/icon/index', NULL, 1, 0, 'C', '0', '0',
     'system:icon:list', 'icon-image', 'admin', NOW(), '', NULL,
     '图标权限归类(页面待建;system:icon 由 IconPicker 组件调用)', '0');

-- 4. 图标 5 按钮去乱码改中文 + 归入「图标管理」菜单
UPDATE `sys_menu` SET `parent_id` = 2242, `menu_name` = '图标列表' WHERE `menu_id` = 2117;
UPDATE `sys_menu` SET `parent_id` = 2242, `menu_name` = '图标详情' WHERE `menu_id` = 2118;
UPDATE `sys_menu` SET `parent_id` = 2242, `menu_name` = '图标新增' WHERE `menu_id` = 2119;
UPDATE `sys_menu` SET `parent_id` = 2242, `menu_name` = '图标修改' WHERE `menu_id` = 2120;
UPDATE `sys_menu` SET `parent_id` = 2242, `menu_name` = '图标删除' WHERE `menu_id` = 2121;
