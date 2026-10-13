-- 迁移:全局提示词并入「系统设置」卡片,隐藏其独立菜单入口
-- 日期:2026-07-07
-- 说明:
--   全局提示词编辑已挪到「系统设置」页(component=system/settings)的卡片里,
--   故把独立菜单「全局提示词」(menu_id=2236,C 菜单)置隐藏 visible='1'——
--   前端 permission store 仅保留 visible='0' 的菜单进路由(见 stores/permission.ts),
--   隐藏后该菜单从导航与路由中被过滤、旧页 prompt/index 不再可达。
--   保留 status='0' 与按钮权限 2237/2238(globalprompt:list/edit):
--   后端 @ss.hasPermi 及系统设置卡片调用的仍是同一批 /system/global-prompt 端点,权限不能丢。
UPDATE `sys_menu` SET `visible` = '1' WHERE `menu_id` = 2236;
