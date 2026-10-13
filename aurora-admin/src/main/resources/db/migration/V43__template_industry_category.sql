-- 案例行业分类：与原有 image/video 媒体类型分离。
-- 预置美发、美甲，并把正式环境存量案例统一迁移到美发。

CREATE TABLE `sys_template_category` (
    `category_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '行业分类ID',
    `name` VARCHAR(32) NOT NULL COMMENT '行业分类名称',
    `sort` INT NOT NULL DEFAULT 0 COMMENT '排序(小在前)',
    `status` CHAR(1) NOT NULL DEFAULT '0' COMMENT '状态(0启用/1停用)',
    `create_by` VARCHAR(64) DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME DEFAULT NULL COMMENT '创建时间',
    `update_by` VARCHAR(64) DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME DEFAULT NULL COMMENT '更新时间',
    `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注',
    `del_flag` CHAR(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除(0存在/2删除)',
    PRIMARY KEY (`category_id`),
    KEY `idx_template_category_status_sort` (`status`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='案例行业分类表';

INSERT INTO `sys_template_category`
    (`category_id`, `name`, `sort`, `status`, `create_by`, `create_time`, `del_flag`)
VALUES
    (1, '美发', 1, '0', 'system', NOW(), '0'),
    (2, '美甲', 2, '0', 'system', NOW(), '0');

ALTER TABLE `template`
    ADD COLUMN `industry_category_id` BIGINT NULL COMMENT '行业分类ID' AFTER `category`;

UPDATE `template`
SET `industry_category_id` = 1
WHERE `industry_category_id` IS NULL;

ALTER TABLE `template`
    MODIFY COLUMN `industry_category_id` BIGINT NOT NULL COMMENT '行业分类ID',
    ADD KEY `idx_template_media_industry_status`
        (`category`, `industry_category_id`, `status`, `del_flag`);

-- 行业分类管理复用案例页弹窗，仅增加按钮权限。
INSERT INTO `sys_menu`
    (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`,
     `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`,
     `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`)
VALUES
    (2260, '行业分类列表', 2224, 10, '', NULL, NULL, 1, 0, 'F', '0', '0', 'template:category:list', '#',
     'admin', NOW(), '', NULL, '', '0'),
    (2261, '行业分类详情', 2224, 11, '', NULL, NULL, 1, 0, 'F', '0', '0', 'template:category:query', '#',
     'admin', NOW(), '', NULL, '', '0'),
    (2262, '行业分类新增', 2224, 12, '', NULL, NULL, 1, 0, 'F', '0', '0', 'template:category:add', '#',
     'admin', NOW(), '', NULL, '', '0'),
    (2263, '行业分类修改', 2224, 13, '', NULL, NULL, 1, 0, 'F', '0', '0', 'template:category:edit', '#',
     'admin', NOW(), '', NULL, '', '0'),
    (2264, '行业分类删除', 2224, 14, '', NULL, NULL, 1, 0, 'F', '0', '0', 'template:category:remove', '#',
     'admin', NOW(), '', NULL, '', '0');
