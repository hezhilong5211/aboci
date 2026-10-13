-- 迁移:移动端二期 · Skill 配置 + 全局提示词(skill / global_prompt 表 + 菜单权限)
-- 日期:2026-06-23
-- 说明:
--   skill:用户 @ 激活时注入的 Skill。inject_prompt 是送给大模型的原始文本,
--     入库前由 SkillServiceImpl 做 HtmlUtils.htmlUnescape 反转义(避免 XSS 过滤器把 " 转成 &quot; 污染提示词);
--     capabilities 逗号分隔,取值 text/vision/image/video;status 0启用/1停用;逻辑删 del_flag(0存在/2删除)。
--   global_prompt:后台维护的全局提示词,全局唯一生效——同一时刻仅一条 is_active=1,保留历史版本;
--     content 同样在 Service 入库前 htmlUnescape;「全局唯一生效」由 Service 事务保证(先全表置0再置目标为1)。
--   sys_menu:在「内容管理」目录(menu_id=2223,V9 已建)下加「Skill 配置」「全局提示词」两菜单 + 按钮权限,
--     menu_id 从 2230 起(避开 V9 已占用至 2229 的区间)。
--     权限串与后端 @ss.hasPermi 对齐:skill:list/query/add/edit/remove、globalprompt:list/edit。
--   字符集统一 utf8mb4_0900_ai_ci。
-- Flyway:本目录受 Flyway 管控,启动时按版本顺序自动执行,仅写增量。

-- ===== Skill 表 =====
CREATE TABLE IF NOT EXISTS `skill` (
    `skill_id`       BIGINT        NOT NULL AUTO_INCREMENT COMMENT 'Skill ID',
    `name`           VARCHAR(64)   NOT NULL COMMENT 'Skill 名称',
    `icon`           VARCHAR(512)  DEFAULT NULL COMMENT '图标 URL',
    `description`    VARCHAR(255)  DEFAULT NULL COMMENT '说明',
    `inject_prompt`  TEXT          DEFAULT NULL COMMENT '注入提示词(送大模型原始文本,入库前 htmlUnescape)',
    `capabilities`   VARCHAR(128)  DEFAULT NULL COMMENT '可用能力范围(逗号分隔:text文本/vision视觉/image生图/video生视频)',
    `sort`           INT           NOT NULL DEFAULT 0 COMMENT '排序(小在前)',
    `status`         CHAR(1)       NOT NULL DEFAULT '0' COMMENT '状态(0启用/1停用)',
    `create_by`      VARCHAR(64)   DEFAULT '' COMMENT '创建者',
    `create_time`    DATETIME      DEFAULT NULL COMMENT '创建时间',
    `update_by`      VARCHAR(64)   DEFAULT '' COMMENT '更新者',
    `update_time`    DATETIME      DEFAULT NULL COMMENT '更新时间',
    `remark`         VARCHAR(255)  DEFAULT NULL COMMENT '备注',
    `del_flag`       CHAR(1)       NOT NULL DEFAULT '0' COMMENT '逻辑删除标记(0存在/2删除)',
    PRIMARY KEY (`skill_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='移动端 Skill 配置表';

-- ===== 全局提示词表 =====
CREATE TABLE IF NOT EXISTS `global_prompt` (
    `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `content`      TEXT          DEFAULT NULL COMMENT '提示词内容(送大模型原始文本,入库前 htmlUnescape)',
    `is_active`    CHAR(1)       NOT NULL DEFAULT '0' COMMENT '是否生效(1生效/0历史),全局唯一仅一条为1',
    `create_by`    VARCHAR(64)   DEFAULT '' COMMENT '创建者',
    `create_time`  DATETIME      DEFAULT NULL COMMENT '创建时间',
    `update_by`    VARCHAR(64)   DEFAULT '' COMMENT '更新者',
    `update_time`  DATETIME      DEFAULT NULL COMMENT '更新时间',
    `remark`       VARCHAR(255)  DEFAULT NULL COMMENT '备注',
    `del_flag`     CHAR(1)       NOT NULL DEFAULT '0' COMMENT '逻辑删除标记(0存在/2删除)',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='移动端全局提示词表(全局唯一生效 + 历史版本)';

-- ===== 菜单:Skill 配置 + 全局提示词(挂在「内容管理」目录 2223 下,menu_id 从 2230 起)=====
-- sys_menu 字段顺序与 V3/V6/V7/V8/V9 保持一致:
--   menu_id, menu_name, parent_id, order_num, path, component, query,
--   is_frame, is_cache, menu_type, visible, status, perms, icon,
--   create_by, create_time, update_by, update_time, remark, del_flag
INSERT INTO `sys_menu`
    (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`,
     `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`,
     `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`)
VALUES
    -- 1. 菜单:Skill 配置(可见,component=skill/index)
    (2230, 'Skill 配置', 2223, 2, 'skill', 'skill/index', NULL, 1, 0, 'C', '0', '0', 'skill:list', 'icon-apps',
     'admin', NOW(), '', NULL, 'Skill 配置管理', '0'),
    -- 2. Skill 按钮权限
    (2231, 'Skill查询', 2230, 1, '', NULL, NULL, 1, 0, 'F', '0', '0', 'skill:list', '#',
     'admin', NOW(), '', NULL, '', '0'),
    (2232, 'Skill详情', 2230, 2, '', NULL, NULL, 1, 0, 'F', '0', '0', 'skill:query', '#',
     'admin', NOW(), '', NULL, '', '0'),
    (2233, 'Skill新增', 2230, 3, '', NULL, NULL, 1, 0, 'F', '0', '0', 'skill:add', '#',
     'admin', NOW(), '', NULL, '', '0'),
    (2234, 'Skill修改', 2230, 4, '', NULL, NULL, 1, 0, 'F', '0', '0', 'skill:edit', '#',
     'admin', NOW(), '', NULL, '', '0'),
    (2235, 'Skill删除', 2230, 5, '', NULL, NULL, 1, 0, 'F', '0', '0', 'skill:remove', '#',
     'admin', NOW(), '', NULL, '', '0'),
    -- 3. 菜单:全局提示词(可见,component=prompt/index)
    (2236, '全局提示词', 2223, 3, 'prompt', 'prompt/index', NULL, 1, 0, 'C', '0', '0', 'globalprompt:list', 'icon-edit',
     'admin', NOW(), '', NULL, '全局提示词管理', '0'),
    -- 4. 全局提示词 按钮权限
    (2237, '全局提示词查询', 2236, 1, '', NULL, NULL, 1, 0, 'F', '0', '0', 'globalprompt:list', '#',
     'admin', NOW(), '', NULL, '', '0'),
    (2238, '全局提示词编辑', 2236, 2, '', NULL, NULL, 1, 0, 'F', '0', '0', 'globalprompt:edit', '#',
     'admin', NOW(), '', NULL, '', '0');
