-- 迁移:移动端二期 · 会员管理(会员等级配置表 + 用户会员段表 + 菜单权限)
-- 日期:2026-06-23
-- 说明:
--   membership_level:会员等级配置(序号/名称/送豆数/价格/启停),逻辑删 del_flag(0存在/2删除)。
--     价格 price 单位为元(DECIMAL(10,2));grant_beans 为每周期送豆数(BIGINT)。
--   user_membership:用户会员段(叠加制——一个用户可有多条有效记录,各自独立到期)。
--     开通时把当时等级信息快照(level_name/level_sort/snapshot_grant_beans/snapshot_price)写入本表,
--     此后删除或修改 membership_level 配置不影响已开通用户权益(读时只依赖本表快照)。
--     level_id 仅作引用、可空(配置被逻辑删后仍保留段);status 0有效/1过期/2取消;索引 (user_id, status)。
--   「当前等级」= 用户有效段(status=0 且 expire_time>now)中 level_sort 最高的一条的 level_name;
--     到期处理为读时判定,本期不做「到期清订阅豆」定时任务。
--   字符集统一 utf8mb4_0900_ai_ci。
--   sys_menu:新增「会员管理」目录 + 「会员等级」「会员列表」两可见菜单 + 按钮权限,menu_id 从 2214 起
--     (避开 V3 2200-2208、V6 2209-2211、V7 2212-2213 已占用区间)。
--     权限串与后端 @ss.hasPermi 对齐:membership:level:list/add/edit/remove、membership:user:list/grant。
-- Flyway:本目录受 Flyway 管控,启动时按版本顺序自动执行,仅写增量。

-- ===== 会员等级配置表 =====
CREATE TABLE IF NOT EXISTS `membership_level` (
    `level_id`     BIGINT        NOT NULL AUTO_INCREMENT COMMENT '等级ID',
    `name`         VARCHAR(64)   NOT NULL COMMENT '等级名称',
    `grant_beans`  BIGINT        NOT NULL DEFAULT 0 COMMENT '每周期送豆数',
    `price`        DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '价格(单位:元,仅展示/记录,本期无支付)',
    `sort`         INT           NOT NULL DEFAULT 0 COMMENT '排序序号(越大等级越高)',
    `status`       CHAR(1)       NOT NULL DEFAULT '0' COMMENT '状态(0启用/1停用)',
    `create_by`    VARCHAR(64)   DEFAULT '' COMMENT '创建者',
    `create_time`  DATETIME      DEFAULT NULL COMMENT '创建时间',
    `update_by`    VARCHAR(64)   DEFAULT '' COMMENT '更新者',
    `update_time`  DATETIME      DEFAULT NULL COMMENT '更新时间',
    `remark`       VARCHAR(255)  DEFAULT NULL COMMENT '备注',
    `del_flag`     CHAR(1)       NOT NULL DEFAULT '0' COMMENT '逻辑删除标记(0存在/2删除)',
    PRIMARY KEY (`level_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='移动端会员等级配置表';

-- ===== 用户会员段表(叠加制,快照购买时等级信息)=====
CREATE TABLE IF NOT EXISTS `user_membership` (
    `id`                   BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`              BIGINT        NOT NULL COMMENT '用户ID',
    `level_id`             BIGINT        DEFAULT NULL COMMENT '引用的等级ID(可空,配置被删后仍保留)',
    `level_sort`           INT           NOT NULL DEFAULT 0 COMMENT '快照:等级序号(取最高用)',
    `level_name`           VARCHAR(64)   NOT NULL COMMENT '快照:等级名称',
    `snapshot_grant_beans` BIGINT        NOT NULL DEFAULT 0 COMMENT '快照:每周期送豆数',
    `snapshot_price`       DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '快照:价格(元)',
    `source`               VARCHAR(16)   NOT NULL COMMENT '来源(MANUAL手动/PAY支付)',
    `start_time`           DATETIME      NOT NULL COMMENT '生效时间',
    `expire_time`          DATETIME      NOT NULL COMMENT '到期时间',
    `status`               CHAR(1)       NOT NULL DEFAULT '0' COMMENT '状态(0有效/1过期/2取消)',
    `create_by`            VARCHAR(64)   DEFAULT '' COMMENT '创建者',
    `create_time`          DATETIME      DEFAULT NULL COMMENT '创建时间',
    `update_by`            VARCHAR(64)   DEFAULT '' COMMENT '更新者',
    `update_time`          DATETIME      DEFAULT NULL COMMENT '更新时间',
    `remark`               VARCHAR(255)  DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    KEY `idx_user_status` (`user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='移动端用户会员段表(叠加制)';

-- ===== 菜单:会员管理(目录 + 两可见菜单 + 按钮权限,menu_id 从 2214 起)=====
-- sys_menu 字段顺序与 V3/V6/V7 保持一致:
--   menu_id, menu_name, parent_id, order_num, path, component, query,
--   is_frame, is_cache, menu_type, visible, status, perms, icon,
--   create_by, create_time, update_by, update_time, remark, del_flag
INSERT INTO `sys_menu`
    (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`,
     `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`,
     `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`)
VALUES
    -- 1. 目录:会员管理(顶级目录 M)
    (2214, '会员管理', 0, 51, 'membership', NULL, NULL, 1, 0, 'M', '0', '0', NULL, 'icon-user-group',
     'admin', NOW(), '', NULL, '会员管理目录', '0'),
    -- 2. 菜单:会员等级(可见,component=membership/level/index)
    (2215, '会员等级', 2214, 1, 'level', 'membership/level/index', NULL, 1, 0, 'C', '0', '0', 'membership:level:list', 'icon-list',
     'admin', NOW(), '', NULL, '会员等级配置', '0'),
    -- 3. 会员等级 按钮权限
    (2216, '会员等级查询', 2215, 1, '', NULL, NULL, 1, 0, 'F', '0', '0', 'membership:level:list', '#',
     'admin', NOW(), '', NULL, '', '0'),
    (2217, '会员等级新增', 2215, 2, '', NULL, NULL, 1, 0, 'F', '0', '0', 'membership:level:add', '#',
     'admin', NOW(), '', NULL, '', '0'),
    (2218, '会员等级修改', 2215, 3, '', NULL, NULL, 1, 0, 'F', '0', '0', 'membership:level:edit', '#',
     'admin', NOW(), '', NULL, '', '0'),
    (2219, '会员等级删除', 2215, 4, '', NULL, NULL, 1, 0, 'F', '0', '0', 'membership:level:remove', '#',
     'admin', NOW(), '', NULL, '', '0'),
    -- 4. 菜单:会员列表(可见,component=membership/user/index)
    (2220, '会员列表', 2214, 2, 'user', 'membership/user/index', NULL, 1, 0, 'C', '0', '0', 'membership:user:list', 'icon-user',
     'admin', NOW(), '', NULL, '会员列表与手动开通', '0'),
    -- 5. 会员列表 按钮权限
    (2221, '会员列表查询', 2220, 1, '', NULL, NULL, 1, 0, 'F', '0', '0', 'membership:user:list', '#',
     'admin', NOW(), '', NULL, '', '0'),
    (2222, '会员手动开通', 2220, 2, '', NULL, NULL, 1, 0, 'F', '0', '0', 'membership:user:grant', '#',
     'admin', NOW(), '', NULL, '', '0');
