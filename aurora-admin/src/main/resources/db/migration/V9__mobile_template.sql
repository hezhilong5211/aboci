-- 迁移:移动端二期 · 案例/模板管理(模板表 + 菜单权限)
-- 日期:2026-06-23
-- 说明:
--   template:案例/模板同一实体——C 端案例页展示=案例,生成页一键带入=模板。本期仅做管理后台 CRUD + 数据模型。
--     category:image 图片 / video 视频(决定参数预设结构);params 存 JSON 字符串(按分类不同结构,原样存取);
--     result_media 存 JSON 数组字符串(效果媒体 URL 列表);use_count 本期仅展示,后续生成带入时 +1。
--     逻辑删 del_flag(0存在/2删除);字符集统一 utf8mb4_0900_ai_ci。
--   sys_menu:新增「内容管理」目录 + 「案例/模板」菜单(component=template/index)+ 按钮权限,
--     menu_id 从 2223 起(避开 V8 已占用至 2222 的区间)。
--     权限串与后端 @ss.hasPermi 对齐:template:list/query/add/edit/remove。
-- Flyway:本目录受 Flyway 管控,启动时按版本顺序自动执行,仅写增量。

-- ===== 案例/模板表 =====
CREATE TABLE IF NOT EXISTS `template` (
    `template_id`  BIGINT        NOT NULL AUTO_INCREMENT COMMENT '模板ID',
    `category`     VARCHAR(8)    NOT NULL COMMENT '分类(image图片/video视频)',
    `title`        VARCHAR(64)   NOT NULL COMMENT '案例标题',
    `cover_url`    VARCHAR(512)  DEFAULT NULL COMMENT '封面图 URL',
    `prompt`       TEXT          NOT NULL COMMENT '提示词文本(必填)',
    `params`       TEXT          DEFAULT NULL COMMENT '参数预设(存 JSON 字符串,结构按分类)',
    `result_media` TEXT          DEFAULT NULL COMMENT '效果媒体 URL 列表(存 JSON 数组字符串)',
    `tags`         VARCHAR(128)  DEFAULT NULL COMMENT '标签(逗号分隔)',
    `use_count`    BIGINT        NOT NULL DEFAULT 0 COMMENT '使用次数(本期仅展示)',
    `sort`         INT           NOT NULL DEFAULT 0 COMMENT '排序(小在前)',
    `status`       CHAR(1)       NOT NULL DEFAULT '0' COMMENT '上下架(0上架/1下架)',
    `create_by`    VARCHAR(64)   DEFAULT '' COMMENT '创建者',
    `create_time`  DATETIME      DEFAULT NULL COMMENT '创建时间',
    `update_by`    VARCHAR(64)   DEFAULT '' COMMENT '更新者',
    `update_time`  DATETIME      DEFAULT NULL COMMENT '更新时间',
    `remark`       VARCHAR(255)  DEFAULT NULL COMMENT '备注',
    `del_flag`     CHAR(1)       NOT NULL DEFAULT '0' COMMENT '逻辑删除标记(0存在/2删除)',
    PRIMARY KEY (`template_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='移动端案例/模板表';

-- ===== 菜单:内容管理(目录 + 案例/模板菜单 + 按钮权限,menu_id 从 2223 起)=====
-- sys_menu 字段顺序与 V3/V6/V7/V8 保持一致:
--   menu_id, menu_name, parent_id, order_num, path, component, query,
--   is_frame, is_cache, menu_type, visible, status, perms, icon,
--   create_by, create_time, update_by, update_time, remark, del_flag
INSERT INTO `sys_menu`
    (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`,
     `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`,
     `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`)
VALUES
    -- 1. 目录:内容管理(顶级目录 M)
    (2223, '内容管理', 0, 52, 'content', NULL, NULL, 1, 0, 'M', '0', '0', NULL, 'icon-bookmark',
     'admin', NOW(), '', NULL, '内容管理目录', '0'),
    -- 2. 菜单:案例/模板(可见,component=template/index)
    (2224, '案例/模板', 2223, 1, 'template', 'template/index', NULL, 1, 0, 'C', '0', '0', 'template:list', 'icon-image',
     'admin', NOW(), '', NULL, '案例/模板管理', '0'),
    -- 3. 案例/模板 按钮权限
    (2225, '案例模板查询', 2224, 1, '', NULL, NULL, 1, 0, 'F', '0', '0', 'template:list', '#',
     'admin', NOW(), '', NULL, '', '0'),
    (2226, '案例模板详情', 2224, 2, '', NULL, NULL, 1, 0, 'F', '0', '0', 'template:query', '#',
     'admin', NOW(), '', NULL, '', '0'),
    (2227, '案例模板新增', 2224, 3, '', NULL, NULL, 1, 0, 'F', '0', '0', 'template:add', '#',
     'admin', NOW(), '', NULL, '', '0'),
    (2228, '案例模板修改', 2224, 4, '', NULL, NULL, 1, 0, 'F', '0', '0', 'template:edit', '#',
     'admin', NOW(), '', NULL, '', '0'),
    (2229, '案例模板删除', 2224, 5, '', NULL, NULL, 1, 0, 'F', '0', '0', 'template:remove', '#',
     'admin', NOW(), '', NULL, '', '0');
