-- 迁移：调整移动端管理菜单——拆成「移动端用户」独立页 + 「系统设置」(tab)
-- 日期：2026-06-21
-- 说明：
--   V3 原把用户列表与微信配置合在一页(mobile/index)。本次拆分为两页：
--   - 2201 移动端用户 → 独立用户列表页 mobile/user/index(path=user)
--   - 2204 微信配置(原隐藏) → 改为可见的「系统设置」页 system/settings/index(path=settings)
--     系统设置做成 tab,第一个 tab「基础配置」含微信小程序 + 微信支付两张配置卡片(点击弹窗编辑)。
--   按钮权限(2202/2203 mobile:user:*、2205-2208 system:wxconfig:*)及目录 2200 不变。

UPDATE `sys_menu`
   SET `path` = 'user', `component` = 'mobile/user/index'
 WHERE `menu_id` = 2201;

UPDATE `sys_menu`
   SET `menu_name` = '系统设置', `path` = 'settings', `component` = 'system/settings/index',
       `visible` = '0', `icon` = 'icon-settings', `remark` = '系统设置(基础配置:微信小程序/支付)'
 WHERE `menu_id` = 2204;
