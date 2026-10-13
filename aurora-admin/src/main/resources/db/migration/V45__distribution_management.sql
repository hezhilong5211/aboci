-- 一级分销管理：业务表、初始关闭配置、动态菜单与权限。
-- 约束：只新增结构和初始配置，不回填历史用户、绑定关系、订单或佣金。

CREATE TABLE `distribution_partner` (
    `partner_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '分销商ID',
    `user_id` BIGINT NOT NULL COMMENT '小程序用户ID',
    `partner_no` VARCHAR(32) NOT NULL COMMENT '分销商编号',
    `invite_code` VARCHAR(32) NOT NULL COMMENT '随机邀请码',
    `status` VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态ACTIVE/DISABLED',
    `enabled_time` DATETIME NOT NULL COMMENT '本次启用时间',
    `disabled_time` DATETIME NULL COMMENT '停用时间',
    `remark` VARCHAR(500) DEFAULT '' COMMENT '运营备注',
    `create_by` VARCHAR(64) DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME DEFAULT NULL COMMENT '创建时间',
    `update_by` VARCHAR(64) DEFAULT '' COMMENT '更新者',
    `update_time` DATETIME DEFAULT NULL COMMENT '更新时间',
    `del_flag` CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志(0存在/2删除)',
    PRIMARY KEY (`partner_id`),
    UNIQUE KEY `uk_distribution_partner_user` (`user_id`),
    UNIQUE KEY `uk_distribution_partner_no` (`partner_no`),
    UNIQUE KEY `uk_distribution_partner_invite` (`invite_code`),
    KEY `idx_distribution_partner_status` (`status`, `del_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='一级分销商';

CREATE TABLE `distribution_relation` (
    `relation_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '分销关系ID',
    `partner_id` BIGINT NOT NULL COMMENT '分销商ID',
    `referred_user_id` BIGINT NOT NULL COMMENT '被推广小程序用户ID',
    `invite_code` VARCHAR(32) NOT NULL COMMENT '绑定时邀请码快照',
    `first_visit_time` DATETIME NULL COMMENT '首次扫码时间',
    `bind_time` DATETIME NOT NULL COMMENT '永久绑定时间',
    `existing_user` TINYINT NOT NULL DEFAULT 0 COMMENT '绑定时是否为老用户(0否/1是)',
    `status` VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '关系状态ACTIVE',
    `create_time` DATETIME NOT NULL COMMENT '创建时间',
    PRIMARY KEY (`relation_id`),
    UNIQUE KEY `uk_distribution_relation_user` (`referred_user_id`),
    KEY `idx_distribution_relation_partner_bind` (`partner_id`, `bind_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='一级分销永久关系';

CREATE TABLE `distribution_visit` (
    `visit_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '访问ID',
    `event_id` VARCHAR(64) NOT NULL COMMENT '客户端访问事件UUID',
    `partner_id` BIGINT NOT NULL COMMENT '分销商ID',
    `user_id` BIGINT NULL COMMENT '登录后回填的小程序用户ID',
    `launch_scene` VARCHAR(64) DEFAULT '' COMMENT '微信启动场景',
    `bound` TINYINT NOT NULL DEFAULT 0 COMMENT '是否形成有效绑定(0否/1是)',
    `visit_time` DATETIME NOT NULL COMMENT '访问时间',
    `bind_time` DATETIME NULL COMMENT '绑定时间',
    PRIMARY KEY (`visit_id`),
    UNIQUE KEY `uk_distribution_visit_event` (`event_id`),
    KEY `idx_distribution_visit_partner_time` (`partner_id`, `visit_time`),
    KEY `idx_distribution_visit_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='分销二维码访问事件';

CREATE TABLE `distribution_config` (
    `config_id` BIGINT NOT NULL COMMENT '固定单例主键',
    `enabled` TINYINT NOT NULL DEFAULT 0 COMMENT '分销总开关(0关闭/1开启)',
    `commission_rate_bps` INT NOT NULL DEFAULT 0 COMMENT '全局佣金比例基点',
    `min_withdraw_amount` BIGINT NOT NULL DEFAULT 0 COMMENT '最低提现金额(分)',
    `create_by` VARCHAR(64) DEFAULT '' COMMENT '创建者',
    `create_time` DATETIME DEFAULT NULL COMMENT '创建时间',
    `update_by` VARCHAR(64) DEFAULT '' COMMENT '修改者',
    `update_time` DATETIME DEFAULT NULL COMMENT '修改时间',
    PRIMARY KEY (`config_id`),
    CONSTRAINT `chk_distribution_config_enabled` CHECK (`enabled` IN (0, 1)),
    CONSTRAINT `chk_distribution_config_rate` CHECK (`commission_rate_bps` BETWEEN 0 AND 10000),
    CONSTRAINT `chk_distribution_config_min_withdraw` CHECK (`min_withdraw_amount` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='分销全局配置';

CREATE TABLE `distribution_account` (
    `partner_id` BIGINT NOT NULL COMMENT '分销商ID',
    `available_amount` BIGINT NOT NULL DEFAULT 0 COMMENT '可提现金额(分)',
    `withdraw_locked_amount` BIGINT NOT NULL DEFAULT 0 COMMENT '提现锁定金额(分)',
    `total_withdrawn` BIGINT NOT NULL DEFAULT 0 COMMENT '累计已提现(分)',
    `total_reversed` BIGINT NOT NULL DEFAULT 0 COMMENT '累计退款冲正(分)',
    `debt_amount` BIGINT NOT NULL DEFAULT 0 COMMENT '待抵扣佣金(分)',
    `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    `update_time` DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`partner_id`),
    CONSTRAINT `chk_distribution_account_available` CHECK (`available_amount` >= 0),
    CONSTRAINT `chk_distribution_account_locked` CHECK (`withdraw_locked_amount` >= 0),
    CONSTRAINT `chk_distribution_account_withdrawn` CHECK (`total_withdrawn` >= 0),
    CONSTRAINT `chk_distribution_account_reversed` CHECK (`total_reversed` >= 0),
    CONSTRAINT `chk_distribution_account_debt` CHECK (`debt_amount` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='分销资金账户';

CREATE TABLE `distribution_commission` (
    `commission_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '佣金ID',
    `partner_id` BIGINT NOT NULL COMMENT '分销商ID',
    `referred_user_id` BIGINT NOT NULL COMMENT '订单用户ID',
    `order_no` VARCHAR(64) NOT NULL COMMENT '支付订单号',
    `product_type` VARCHAR(32) NOT NULL COMMENT '商品类型快照',
    `product_name` VARCHAR(255) DEFAULT '' COMMENT '商品名称快照',
    `order_amount` BIGINT NOT NULL COMMENT '订单实付金额(分)',
    `rate_bps` INT NOT NULL COMMENT '佣金比例快照',
    `commission_amount` BIGINT NOT NULL COMMENT '原始佣金(分)',
    `available_amount` BIGINT NOT NULL DEFAULT 0 COMMENT '当前可提现部分(分)',
    `locked_amount` BIGINT NOT NULL DEFAULT 0 COMMENT '提现锁定部分(分)',
    `withdrawn_amount` BIGINT NOT NULL DEFAULT 0 COMMENT '已提现部分(分)',
    `reversed_amount` BIGINT NOT NULL DEFAULT 0 COMMENT '已冲正部分(分)',
    `debt_offset_amount` BIGINT NOT NULL DEFAULT 0 COMMENT '已抵扣历史欠款部分(分)',
    `status` VARCHAR(16) NOT NULL COMMENT 'AVAILABLE/PARTIAL/LOCKED/WITHDRAWN/REVERSED',
    `pay_time` DATETIME NOT NULL COMMENT '订单支付时间',
    `refund_no` VARCHAR(64) NULL COMMENT '退款编号',
    `refund_time` DATETIME NULL COMMENT '退款时间',
    `create_time` DATETIME NOT NULL COMMENT '创建时间',
    `update_time` DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`commission_id`),
    UNIQUE KEY `uk_distribution_commission_order` (`order_no`),
    KEY `idx_distribution_commission_partner_pay` (`partner_id`, `pay_time`),
    KEY `idx_distribution_commission_user_pay` (`referred_user_id`, `pay_time`),
    KEY `idx_distribution_commission_status` (`status`, `pay_time`),
    CONSTRAINT `chk_distribution_commission_amounts` CHECK (
        `order_amount` >= 0
        AND `rate_bps` BETWEEN 0 AND 10000
        AND `commission_amount` >= 0
        AND `available_amount` >= 0
        AND `locked_amount` >= 0
        AND `withdrawn_amount` >= 0
        AND `reversed_amount` >= 0
        AND `debt_offset_amount` >= 0
        AND `available_amount` + `locked_amount` + `withdrawn_amount`
            + `reversed_amount` + `debt_offset_amount` = `commission_amount`
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单分销佣金';

CREATE TABLE `distribution_account_transaction` (
    `transaction_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '资金流水ID',
    `partner_id` BIGINT NOT NULL COMMENT '分销商ID',
    `type` VARCHAR(32) NOT NULL COMMENT '流水类型',
    `change_amount` BIGINT NOT NULL COMMENT '变动金额(分)',
    `available_after` BIGINT NOT NULL COMMENT '变动后可提现金额(分)',
    `locked_after` BIGINT NOT NULL COMMENT '变动后锁定金额(分)',
    `debt_after` BIGINT NOT NULL COMMENT '变动后待抵扣金额(分)',
    `biz_no` VARCHAR(128) NOT NULL COMMENT '幂等业务编号',
    `source_order_no` VARCHAR(64) NULL COMMENT '来源支付订单号',
    `withdrawal_no` VARCHAR(64) NULL COMMENT '提现单号',
    `remark` VARCHAR(500) DEFAULT '' COMMENT '中文说明',
    `create_time` DATETIME NOT NULL COMMENT '创建时间',
    PRIMARY KEY (`transaction_id`),
    UNIQUE KEY `uk_distribution_transaction_biz` (`type`, `biz_no`),
    KEY `idx_distribution_transaction_partner_time` (`partner_id`, `create_time`),
    KEY `idx_distribution_transaction_order` (`source_order_no`),
    KEY `idx_distribution_transaction_withdrawal` (`withdrawal_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='不可修改的分销资金流水';

CREATE TABLE `distribution_withdrawal` (
    `withdrawal_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '提现ID',
    `withdrawal_no` VARCHAR(64) NOT NULL COMMENT '提现单号',
    `partner_id` BIGINT NOT NULL COMMENT '分销商ID',
    `apply_amount` BIGINT NOT NULL COMMENT '申请金额(分)',
    `method_type` VARCHAR(16) NOT NULL COMMENT 'WECHAT_QR/ALIPAY_QR/BANK_CARD',
    `payee_name` VARCHAR(64) NOT NULL COMMENT '收款人姓名',
    `bank_name` VARCHAR(128) NULL COMMENT '银行名称',
    `bank_account_cipher` VARCHAR(1024) NULL COMMENT '银行卡号密文',
    `bank_account_masked` VARCHAR(64) NULL COMMENT '银行卡号脱敏值',
    `receipt_qr_file_key` VARCHAR(255) NULL COMMENT '私有收款码文件键',
    `status` VARCHAR(16) NOT NULL DEFAULT 'SUBMITTED' COMMENT 'SUBMITTED/NEEDS_REVIEW/PAID/REJECTED',
    `reject_reason` VARCHAR(500) NULL COMMENT '驳回原因',
    `payment_reference` VARCHAR(128) NULL COMMENT '后台打款流水号',
    `payment_proof_file_key` VARCHAR(255) NULL COMMENT '私有付款凭证文件键',
    `apply_time` DATETIME NOT NULL COMMENT '申请时间',
    `audit_time` DATETIME NULL COMMENT '审核时间',
    `paid_time` DATETIME NULL COMMENT '打款时间',
    `audit_by` VARCHAR(64) NULL COMMENT '审核管理员',
    `pay_by` VARCHAR(64) NULL COMMENT '打款管理员',
    `create_time` DATETIME NOT NULL COMMENT '创建时间',
    `update_time` DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`withdrawal_id`),
    UNIQUE KEY `uk_distribution_withdrawal_no` (`withdrawal_no`),
    KEY `idx_distribution_withdrawal_partner_time` (`partner_id`, `apply_time`),
    KEY `idx_distribution_withdrawal_status_time` (`status`, `apply_time`),
    CONSTRAINT `chk_distribution_withdrawal_amount` CHECK (`apply_amount` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='分销提现单';

CREATE TABLE `distribution_withdrawal_allocation` (
    `allocation_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '提现分配ID',
    `withdrawal_id` BIGINT NOT NULL COMMENT '提现ID',
    `commission_id` BIGINT NOT NULL COMMENT '佣金ID',
    `allocated_amount` BIGINT NOT NULL COMMENT '本次锁定金额(分)',
    `status` VARCHAR(16) NOT NULL DEFAULT 'LOCKED' COMMENT 'LOCKED/RESTORED/PAID/REVERSED',
    `create_time` DATETIME NOT NULL COMMENT '创建时间',
    `update_time` DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`allocation_id`),
    UNIQUE KEY `uk_distribution_allocation_pair` (`withdrawal_id`, `commission_id`),
    KEY `idx_distribution_allocation_commission` (`commission_id`, `status`),
    CONSTRAINT `chk_distribution_allocation_amount` CHECK (`allocated_amount` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='提现佣金分配明细';

INSERT INTO `distribution_config`
    (`config_id`, `enabled`, `commission_rate_bps`, `min_withdraw_amount`, `create_by`, `create_time`)
VALUES
    (1, 0, 0, 0, 'system', NOW());

-- 当前迁移文件菜单最大ID为2264，预留2265至2269后使用2270至2290。
INSERT INTO `sys_menu`
    (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`,
     `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`,
     `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`)
VALUES
    (2270, '分销管理', 0, 20, 'distribution', NULL, NULL, 1, 0, 'M', '0', '0', NULL, 'icon-share-alt', 'admin', NOW(), '', NULL, '一级分销管理目录', '0'),
    (2271, '分销概览', 2270, 1, 'overview', 'distribution/overview/index', NULL, 1, 0, 'C', '0', '0', 'distribution:overview:list', 'icon-dashboard', 'admin', NOW(), '', NULL, '', '0'),
    (2272, '分销概览查询', 2271, 1, '', NULL, NULL, 1, 0, 'F', '0', '0', 'distribution:overview:list', '#', 'admin', NOW(), '', NULL, '', '0'),
    (2273, '分销商管理', 2270, 2, 'partner', 'distribution/partner/index', NULL, 1, 0, 'C', '0', '0', 'distribution:partner:list', 'icon-user-group', 'admin', NOW(), '', NULL, '', '0'),
    (2274, '分销商查询', 2273, 1, '', NULL, NULL, 1, 0, 'F', '0', '0', 'distribution:partner:list', '#', 'admin', NOW(), '', NULL, '', '0'),
    (2275, '分销商详情', 2273, 2, '', NULL, NULL, 1, 0, 'F', '0', '0', 'distribution:partner:query', '#', 'admin', NOW(), '', NULL, '', '0'),
    (2276, '分销商新增', 2273, 3, '', NULL, NULL, 1, 0, 'F', '0', '0', 'distribution:partner:add', '#', 'admin', NOW(), '', NULL, '', '0'),
    (2277, '分销商修改', 2273, 4, '', NULL, NULL, 1, 0, 'F', '0', '0', 'distribution:partner:edit', '#', 'admin', NOW(), '', NULL, '', '0'),
    (2278, '佣金明细', 2270, 3, 'commission', 'distribution/commission/index', NULL, 1, 0, 'C', '0', '0', 'distribution:commission:list', 'icon-list', 'admin', NOW(), '', NULL, '', '0'),
    (2279, '佣金列表', 2278, 1, '', NULL, NULL, 1, 0, 'F', '0', '0', 'distribution:commission:list', '#', 'admin', NOW(), '', NULL, '', '0'),
    (2280, '佣金详情', 2278, 2, '', NULL, NULL, 1, 0, 'F', '0', '0', 'distribution:commission:query', '#', 'admin', NOW(), '', NULL, '', '0'),
    (2281, '佣金调整', 2278, 3, '', NULL, NULL, 1, 0, 'F', '0', '0', 'distribution:commission:adjust', '#', 'admin', NOW(), '', NULL, '', '0'),
    (2282, '提现管理', 2270, 4, 'withdrawal', 'distribution/withdrawal/index', NULL, 1, 0, 'C', '0', '0', 'distribution:withdrawal:list', 'icon-wallet', 'admin', NOW(), '', NULL, '', '0'),
    (2283, '提现列表', 2282, 1, '', NULL, NULL, 1, 0, 'F', '0', '0', 'distribution:withdrawal:list', '#', 'admin', NOW(), '', NULL, '', '0'),
    (2284, '提现详情', 2282, 2, '', NULL, NULL, 1, 0, 'F', '0', '0', 'distribution:withdrawal:query', '#', 'admin', NOW(), '', NULL, '', '0'),
    (2285, '提现审核', 2282, 3, '', NULL, NULL, 1, 0, 'F', '0', '0', 'distribution:withdrawal:audit', '#', 'admin', NOW(), '', NULL, '', '0'),
    (2286, '确认打款', 2282, 4, '', NULL, NULL, 1, 0, 'F', '0', '0', 'distribution:withdrawal:pay', '#', 'admin', NOW(), '', NULL, '', '0'),
    (2287, '查看敏感资料', 2282, 5, '', NULL, NULL, 1, 0, 'F', '0', '0', 'distribution:withdrawal:sensitive', '#', 'admin', NOW(), '', NULL, '', '0'),
    (2288, '分销设置', 2270, 5, 'config', 'distribution/config/index', NULL, 1, 0, 'C', '0', '0', 'distribution:config:query', 'icon-settings', 'admin', NOW(), '', NULL, '', '0'),
    (2289, '分销设置查询', 2288, 1, '', NULL, NULL, 1, 0, 'F', '0', '0', 'distribution:config:query', '#', 'admin', NOW(), '', NULL, '', '0'),
    (2290, '分销设置修改', 2288, 2, '', NULL, NULL, 1, 0, 'F', '0', '0', 'distribution:config:edit', '#', 'admin', NOW(), '', NULL, '', '0');
