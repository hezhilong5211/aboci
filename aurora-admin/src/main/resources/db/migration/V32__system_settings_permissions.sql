-- 修复「系统设置」聚合页面的权限树：
-- 1. 分享配置改用专用权限，避免给角色开放可读写全部 sys_config 的 system:config:*。
-- 2. 为已拥有系统设置的角色回填读取权限；编辑权限按旧分享配置实际使用的参数编辑权限迁移。
-- 全局提示词继续保留原权限节点和角色关联，前端按其独立权限决定是否加载该卡片。

-- 分享配置专用权限节点。
INSERT INTO `sys_menu`
    (`menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`,
     `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`,
     `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`)
VALUES
    ('分享配置查询', 2204, 5, '', NULL, NULL, 1, 0, 'F', '0', '0', 'system:shareconfig:list', '#',
     'admin', NOW(), '', NULL, '仅查询 mobile.share.title/image', '0');
SET @share_config_list_menu_id = LAST_INSERT_ID();

INSERT INTO `sys_menu`
    (`menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`,
     `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`,
     `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`)
VALUES
    ('分享配置编辑', 2204, 6, '', NULL, NULL, 1, 0, 'F', '0', '0', 'system:shareconfig:edit', '#',
     'admin', NOW(), '', NULL, '仅修改 mobile.share.title/image', '0');
SET @share_config_edit_menu_id = LAST_INSERT_ID();

-- 读取分享配置：已有系统设置菜单的角色都需要，避免进页面即 403。
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT `role_id`, @share_config_list_menu_id
  FROM `sys_role_menu`
 WHERE `menu_id` = 2204;

-- 编辑分享配置：从改造前真实控制分享保存的「参数设置修改」权限迁移，不扩大权限。
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT `role_id`, @share_config_edit_menu_id
  FROM `sys_role_menu`
 WHERE `menu_id` = 2020;
