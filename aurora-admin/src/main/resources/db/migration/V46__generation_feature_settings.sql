-- AI 生成页总开关、下载水印开关及系统设置专用权限。
-- 只新增默认配置和权限，不修改历史生成任务、图片、会员或导航数据。

INSERT INTO `sys_config`
    (`config_name`, `config_key`, `config_value`, `config_type`,
     `create_by`, `create_time`, `update_by`, `update_time`, `remark`)
VALUES
    ('AI 生成功能', 'mobile.generation.enabled', 'true', 'Y',
     'admin', NOW(), '', NULL, '控制小程序独立生成页面及其提交接口'),
    ('生成图片下载水印', 'mobile.generation.download_watermark.enabled', 'true', 'Y',
     'admin', NOW(), '', NULL, '控制保存到相册的生成图片是否包含 AI 标识');

INSERT INTO `sys_menu`
    (`menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`,
     `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`,
     `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`)
VALUES
    ('AI 生成配置查询', 2204, 7, '', NULL, NULL, 1, 0, 'F', '0', '0',
     'system:generationconfig:list', '#', 'admin', NOW(), '', NULL,
     '仅查询 mobile.generation.* 固定配置', '0');
SET @generation_config_list_menu_id = LAST_INSERT_ID();

INSERT INTO `sys_menu`
    (`menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`,
     `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`,
     `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`)
VALUES
    ('AI 生成配置编辑', 2204, 8, '', NULL, NULL, 1, 0, 'F', '0', '0',
     'system:generationconfig:edit', '#', 'admin', NOW(), '', NULL,
     '仅修改 mobile.generation.* 固定配置', '0');
SET @generation_config_edit_menu_id = LAST_INSERT_ID();

-- 已有系统设置权限的角色可读取新卡片。
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT `role_id`, @generation_config_list_menu_id
  FROM `sys_role_menu`
 WHERE `menu_id` = 2204;

-- 编辑权限沿用现有分享配置编辑角色，不扩大系统设置写权限。
INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT `role_id`, @generation_config_edit_menu_id
  FROM `sys_role_menu`
 WHERE `menu_id` = (
     SELECT `menu_id`
       FROM `sys_menu`
      WHERE `perms` = 'system:shareconfig:edit'
        AND `del_flag` = '0'
      ORDER BY `menu_id`
      LIMIT 1
 );
