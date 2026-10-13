-- V26:修正豆子套餐「详情」按钮权限串
-- V23 误把套餐详情按钮(menu_id=2244)的 perms 写成 mobile:beanpackage:list;
-- 而后台详情接口 BeanRechargePackageController 用 @ss.hasPermi('mobile:beanpackage:query'),
-- 导致仅授 list 的非超管账号访问套餐详情会 403。改为 query,并把菜单名规范为「豆子套餐详情」。
UPDATE `sys_menu` SET `menu_name` = '豆子套餐详情', `perms` = 'mobile:beanpackage:query'
WHERE `menu_id` = 2244 AND `perms` = 'mobile:beanpackage:list';
