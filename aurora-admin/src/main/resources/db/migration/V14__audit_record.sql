-- 迁移:移动端二期 · 内容审核记录(审核流水表 + 菜单权限)
-- 日期:2026-06-24
-- 说明:
--   audit_record:文本/媒体送审存证流水表。本期仅 text(对话输入 chat_input),媒体相关列预留。
--     object_type=text/media(本期 text);biz_type=chat_input;suggest=审核服务建议(pass/review/risky/error,对齐 ContentCheckResult);
--     result=最终处置(PASS/REJECT/ERROR);content_snippet 截断存证(VARCHAR(500));
--     task_id/trace_id 预留媒体异步审核;conversation_id 关联对话内输入。
--     流水表:无审计五件套、无 del_flag(不逻辑删);仅 create_time(DB default 兜底 + 实体 @TableField(fill=INSERT) 回填)。
--   sys_menu:在「内容管理」目录(menu_id=2223)下新增「内容审核日志」C 菜单 + 列表按钮权限。
--     选 2223 为父目录依据:AI Config(2005)/模型配置(2000)已 del_flag=2 软删不可用;2223 可见正常,
--     且已聚合 案例/模板、Skill 配置、全局提示词 等内容运营菜单,语义最贴「内容审核日志」。
--     menu_id 接续现有最大值(2238)取 2240/2241;order_num 接续 2223 同级(现有最大 3)取 4。
--     权限串 ai:audit:list 与后端 @ss.hasPermi 对齐(component=ai/audit/index)。
-- 约束:MySQL 9.6 已移除 MD5() 等函数,本脚本不使用;Flyway 仅写增量,禁 DROP/TRUNCATE。

-- ===== 内容审核记录表(送审存证流水)=====
CREATE TABLE IF NOT EXISTS `audit_record` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '审核记录ID',
    `user_id`         BIGINT       NOT NULL COMMENT '送审用户(mobile_user.id)',
    `object_type`     VARCHAR(20)  NOT NULL COMMENT '对象类型(text文本/media媒体,本期 text)',
    `biz_type`        VARCHAR(20)  NOT NULL COMMENT '业务类型(chat_input 对话输入)',
    `conversation_id` BIGINT       DEFAULT NULL COMMENT '关联会话ID(对话内输入,可空)',
    `task_id`         VARCHAR(64)  DEFAULT NULL COMMENT '关联任务ID(预留媒体异步审核)',
    `content_snippet` VARCHAR(500) DEFAULT NULL COMMENT '送审文本截断存证(超长截断)',
    `suggest`         VARCHAR(20)  DEFAULT NULL COMMENT '审核建议(pass/review/risky/error)',
    `label`           VARCHAR(50)  DEFAULT NULL COMMENT '命中标签(可空)',
    `result`          VARCHAR(20)  DEFAULT NULL COMMENT '最终处置(PASS/REJECT/ERROR)',
    `reason`          VARCHAR(500) DEFAULT NULL COMMENT '违规提示/原因(脱敏,可空)',
    `trace_id`        VARCHAR(64)  DEFAULT NULL COMMENT '链路追踪ID(预留媒体异步)',
    `create_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user` (`user_id`),
    KEY `idx_object_type` (`object_type`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='内容审核记录(送审存证流水)';

-- ===== 菜单:内容审核日志(挂「内容管理」目录 2223,C 菜单 + 列表按钮)=====
-- sys_menu 字段顺序与 V9 等历史脚本保持一致:
--   menu_id, menu_name, parent_id, order_num, path, component, query,
--   is_frame, is_cache, menu_type, visible, status, perms, icon,
--   create_by, create_time, update_by, update_time, remark, del_flag
INSERT INTO `sys_menu`
    (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`,
     `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`,
     `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`)
VALUES
    -- 1. 菜单:内容审核日志(可见,component=ai/audit/index)
    (2240, '内容审核日志', 2223, 4, 'audit', 'ai/audit/index', NULL, 1, 0, 'C', '0', '0', 'ai:audit:list', 'icon-safe',
     'admin', NOW(), '', NULL, '内容审核日志(送审存证)', '0'),
    -- 2. 列表查询 按钮权限(纯查询展示页,仅列表 F,对齐全局提示词仅 :list 的模式)
    (2241, '审核日志查询', 2240, 1, '', NULL, NULL, 1, 0, 'F', '0', '0', 'ai:audit:list', '#',
     'admin', NOW(), '', NULL, '', '0');
