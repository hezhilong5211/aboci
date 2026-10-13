-- ============================================================
-- aboci 美业助手 数据库初始化脚本
-- 生成日期:2026-10-12    对应迁移版本:V49
--
-- 内容:全部 46 张表的结构 + 系统初始化数据
--       (菜单、字典、参数、角色、根部门、管理员账号、迁移版本记录)
--       不含业务数据,模型密钥、微信配置均为空,部署后在后台填写。
--
-- 用法:新建空库(字符集 utf8mb4,排序规则 utf8mb4_0900_ai_ci)后导入
--       mysql --default-character-set=utf8mb4 -uroot -p 库名 < aurora_framework_full_20261012.sql
--
-- 注意:只用于空库首次初始化。脚本不带 DROP TABLE,库里已有同名表会直接报错,不会覆盖数据。
--       导入后启动应用,Flyway 识别到库已在 V49,不会重复执行迁移。
--       后台管理员账号 admin,初始密码另行提供,首次登录后请修改。
-- ============================================================

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `audit_record`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `audit_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '审核记录ID',
  `user_id` bigint NOT NULL COMMENT '送审用户(mobile_user.id)',
  `object_type` varchar(20) NOT NULL COMMENT '对象类型(text文本/media媒体,本期 text)',
  `biz_type` varchar(20) NOT NULL COMMENT '业务类型(chat_input 对话输入)',
  `conversation_id` bigint DEFAULT NULL COMMENT '关联会话ID(对话内输入,可空)',
  `task_id` varchar(64) DEFAULT NULL COMMENT '关联任务ID(预留媒体异步审核)',
  `content_snippet` varchar(500) DEFAULT NULL COMMENT '送审文本截断存证(超长截断)',
  `suggest` varchar(20) DEFAULT NULL COMMENT '审核建议(pass/review/risky/error)',
  `label` varchar(50) DEFAULT NULL COMMENT '命中标签(可空)',
  `result` varchar(20) DEFAULT NULL COMMENT '最终处置(PASS/REJECT/ERROR)',
  `reason` varchar(500) DEFAULT NULL COMMENT '违规提示/原因(脱敏,可空)',
  `trace_id` varchar(64) DEFAULT NULL COMMENT '链路追踪ID(预留媒体异步)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_object_type` (`object_type`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='内容审核记录(送审存证流水)';
/*!40101 SET character_set_client = @saved_cs_client */;


--
-- Table structure for table `bean_account`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bean_account` (
  `account_id` bigint NOT NULL AUTO_INCREMENT COMMENT '账户ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `balance` bigint NOT NULL DEFAULT '0' COMMENT '当前余额(≥0)',
  `total_earned` bigint NOT NULL DEFAULT '0' COMMENT '累计获得豆子',
  `total_consumed` bigint NOT NULL DEFAULT '0' COMMENT '累计消耗豆子',
  `total_expired` bigint NOT NULL DEFAULT '0' COMMENT '累计因会员到期清零的豆子',
  `total_revoked` bigint NOT NULL DEFAULT '0' COMMENT '累计因支付订单退款回收的豆子',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`account_id`),
  UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='移动端豆子账户表';
/*!40101 SET character_set_client = @saved_cs_client */;


--
-- Table structure for table `bean_recharge_package`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bean_recharge_package` (
  `package_id` bigint NOT NULL AUTO_INCREMENT COMMENT '套餐ID',
  `name` varchar(64) DEFAULT NULL COMMENT '套餐名称',
  `virtual_product_id` varchar(20) DEFAULT NULL COMMENT '微信虚拟支付普通道具ID',
  `beans` bigint NOT NULL DEFAULT '0' COMMENT '到账豆数',
  `price` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '价格(元)',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序(小在前)',
  `status` char(1) NOT NULL DEFAULT '0' COMMENT '状态(0上架/1下架)',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `del_flag` char(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除标记(0存在/2删除)',
  PRIMARY KEY (`package_id`),
  UNIQUE KEY `uk_bean_package_virtual_product` (`virtual_product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='移动端豆子充值套餐表';
/*!40101 SET character_set_client = @saved_cs_client */;


--
-- Table structure for table `bean_transaction`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bean_transaction` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `type` varchar(16) NOT NULL COMMENT '类型(GIFT/ISSUE/RECHARGE/DEDUCT/REFUND/ADJUST/EXPIRE/REVOKE/RENEW_RESTORE)',
  `change_value` bigint NOT NULL COMMENT '变动值(正负)',
  `balance_after` bigint NOT NULL COMMENT '变动后余额快照',
  `biz_no` varchar(64) DEFAULT NULL COMMENT '关联业务单号(可空)',
  `source_biz_no` varchar(128) DEFAULT NULL COMMENT '来源业务单号(续费恢复时为旧EXPIRE单号)',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_type_source_biz` (`type`,`source_biz_no`),
  KEY `idx_user_create` (`user_id`,`create_time`),
  KEY `idx_user_type_biz` (`user_id`,`type`,`biz_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='移动端豆子流水表';
/*!40101 SET character_set_client = @saved_cs_client */;


--
-- Table structure for table `conversation`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `conversation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '会话ID',
  `user_id` bigint NOT NULL COMMENT 'mobile_user.id(取自登录态)',
  `title` varchar(255) DEFAULT NULL COMMENT '会话标题(首条消息摘要或「新对话」)',
  `model_id` bigint DEFAULT NULL COMMENT 'DeepSeek 模型 id(默认取 active chat 模型)',
  `last_message_time` datetime DEFAULT NULL COMMENT '最近消息时间(列表倒序用)',
  `expire_time` datetime NOT NULL COMMENT '过期时间(create_time + 30 天,清理用)',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `del_flag` char(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除标记(0存在/2删除)',
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`,`del_flag`),
  KEY `idx_expire` (`expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 闭环对话会话头';
/*!40101 SET character_set_client = @saved_cs_client */;


--
-- Table structure for table `distribution_account`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `distribution_account` (
  `partner_id` bigint NOT NULL COMMENT '分销商ID',
  `available_amount` bigint NOT NULL DEFAULT '0' COMMENT '可提现金额(分)',
  `withdraw_locked_amount` bigint NOT NULL DEFAULT '0' COMMENT '提现锁定金额(分)',
  `total_withdrawn` bigint NOT NULL DEFAULT '0' COMMENT '累计已提现(分)',
  `total_reversed` bigint NOT NULL DEFAULT '0' COMMENT '累计退款冲正(分)',
  `debt_amount` bigint NOT NULL DEFAULT '0' COMMENT '待抵扣佣金(分)',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `update_time` datetime NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`partner_id`),
  CONSTRAINT `chk_distribution_account_available` CHECK ((`available_amount` >= 0)),
  CONSTRAINT `chk_distribution_account_debt` CHECK ((`debt_amount` >= 0)),
  CONSTRAINT `chk_distribution_account_locked` CHECK ((`withdraw_locked_amount` >= 0)),
  CONSTRAINT `chk_distribution_account_reversed` CHECK ((`total_reversed` >= 0)),
  CONSTRAINT `chk_distribution_account_withdrawn` CHECK ((`total_withdrawn` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='分销资金账户';
/*!40101 SET character_set_client = @saved_cs_client */;


--
-- Table structure for table `distribution_account_transaction`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `distribution_account_transaction` (
  `transaction_id` bigint NOT NULL AUTO_INCREMENT COMMENT '资金流水ID',
  `partner_id` bigint NOT NULL COMMENT '分销商ID',
  `type` varchar(32) NOT NULL COMMENT '流水类型',
  `change_amount` bigint NOT NULL COMMENT '变动金额(分)',
  `available_after` bigint NOT NULL COMMENT '变动后可提现金额(分)',
  `locked_after` bigint NOT NULL COMMENT '变动后锁定金额(分)',
  `debt_after` bigint NOT NULL COMMENT '变动后待抵扣金额(分)',
  `biz_no` varchar(128) NOT NULL COMMENT '幂等业务编号',
  `source_order_no` varchar(64) DEFAULT NULL COMMENT '来源支付订单号',
  `withdrawal_no` varchar(64) DEFAULT NULL COMMENT '提现单号',
  `remark` varchar(500) DEFAULT '' COMMENT '中文说明',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`transaction_id`),
  UNIQUE KEY `uk_distribution_transaction_biz` (`type`,`biz_no`),
  KEY `idx_distribution_transaction_partner_time` (`partner_id`,`create_time`),
  KEY `idx_distribution_transaction_order` (`source_order_no`),
  KEY `idx_distribution_transaction_withdrawal` (`withdrawal_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='不可修改的分销资金流水';
/*!40101 SET character_set_client = @saved_cs_client */;


--
-- Table structure for table `distribution_commission`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `distribution_commission` (
  `commission_id` bigint NOT NULL AUTO_INCREMENT COMMENT '佣金ID',
  `partner_id` bigint NOT NULL COMMENT '分销商ID',
  `referred_user_id` bigint NOT NULL COMMENT '订单用户ID',
  `order_no` varchar(64) NOT NULL COMMENT '支付订单号',
  `product_type` varchar(32) NOT NULL COMMENT '商品类型快照',
  `product_name` varchar(255) DEFAULT '' COMMENT '商品名称快照',
  `order_amount` bigint NOT NULL COMMENT '订单实付金额(分)',
  `rate_bps` int NOT NULL COMMENT '佣金比例快照',
  `commission_amount` bigint NOT NULL COMMENT '原始佣金(分)',
  `available_amount` bigint NOT NULL DEFAULT '0' COMMENT '当前可提现部分(分)',
  `locked_amount` bigint NOT NULL DEFAULT '0' COMMENT '提现锁定部分(分)',
  `withdrawn_amount` bigint NOT NULL DEFAULT '0' COMMENT '已提现部分(分)',
  `reversed_amount` bigint NOT NULL DEFAULT '0' COMMENT '已冲正部分(分)',
  `debt_offset_amount` bigint NOT NULL DEFAULT '0' COMMENT '已抵扣历史欠款部分(分)',
  `status` varchar(16) NOT NULL COMMENT 'AVAILABLE/PARTIAL/LOCKED/WITHDRAWN/REVERSED',
  `pay_time` datetime NOT NULL COMMENT '订单支付时间',
  `refund_no` varchar(64) DEFAULT NULL COMMENT '退款编号',
  `refund_time` datetime DEFAULT NULL COMMENT '退款时间',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`commission_id`),
  UNIQUE KEY `uk_distribution_commission_order` (`order_no`),
  KEY `idx_distribution_commission_partner_pay` (`partner_id`,`pay_time`),
  KEY `idx_distribution_commission_user_pay` (`referred_user_id`,`pay_time`),
  KEY `idx_distribution_commission_status` (`status`,`pay_time`),
  CONSTRAINT `chk_distribution_commission_amounts` CHECK (((`order_amount` >= 0) and (`rate_bps` between 0 and 10000) and (`commission_amount` >= 0) and (`available_amount` >= 0) and (`locked_amount` >= 0) and (`withdrawn_amount` >= 0) and (`reversed_amount` >= 0) and (`debt_offset_amount` >= 0) and (((((`available_amount` + `locked_amount`) + `withdrawn_amount`) + `reversed_amount`) + `debt_offset_amount`) = `commission_amount`)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单分销佣金';
/*!40101 SET character_set_client = @saved_cs_client */;


--
-- Table structure for table `distribution_config`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `distribution_config` (
  `config_id` bigint NOT NULL COMMENT '固定单例主键',
  `enabled` tinyint NOT NULL DEFAULT '0' COMMENT '分销总开关(0关闭/1开启)',
  `commission_rate_bps` int NOT NULL DEFAULT '0' COMMENT '全局佣金比例基点',
  `min_withdraw_amount` bigint NOT NULL DEFAULT '0' COMMENT '最低提现金额(分)',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '修改者',
  `update_time` datetime DEFAULT NULL COMMENT '修改时间',
  PRIMARY KEY (`config_id`),
  CONSTRAINT `chk_distribution_config_enabled` CHECK ((`enabled` in (0,1))),
  CONSTRAINT `chk_distribution_config_min_withdraw` CHECK ((`min_withdraw_amount` >= 0)),
  CONSTRAINT `chk_distribution_config_rate` CHECK ((`commission_rate_bps` between 0 and 10000))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='分销全局配置';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `distribution_config`
--

INSERT INTO `distribution_config` (`config_id`, `enabled`, `commission_rate_bps`, `min_withdraw_amount`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES (1,0,0,0,'system','2026-10-12 23:01:16','',NULL);

--
-- Table structure for table `distribution_partner`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `distribution_partner` (
  `partner_id` bigint NOT NULL AUTO_INCREMENT COMMENT '分销商ID',
  `user_id` bigint NOT NULL COMMENT '小程序用户ID',
  `partner_no` varchar(32) NOT NULL COMMENT '分销商编号',
  `invite_code` varchar(32) NOT NULL COMMENT '随机邀请码',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态ACTIVE/DISABLED',
  `enabled_time` datetime NOT NULL COMMENT '本次启用时间',
  `disabled_time` datetime DEFAULT NULL COMMENT '停用时间',
  `remark` varchar(500) DEFAULT '' COMMENT '运营备注',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `del_flag` char(1) NOT NULL DEFAULT '0' COMMENT '删除标志(0存在/2删除)',
  PRIMARY KEY (`partner_id`),
  UNIQUE KEY `uk_distribution_partner_user` (`user_id`),
  UNIQUE KEY `uk_distribution_partner_no` (`partner_no`),
  UNIQUE KEY `uk_distribution_partner_invite` (`invite_code`),
  KEY `idx_distribution_partner_status` (`status`,`del_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='一级分销商';
/*!40101 SET character_set_client = @saved_cs_client */;


--
-- Table structure for table `distribution_relation`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `distribution_relation` (
  `relation_id` bigint NOT NULL AUTO_INCREMENT COMMENT '分销关系ID',
  `partner_id` bigint NOT NULL COMMENT '分销商ID',
  `referred_user_id` bigint NOT NULL COMMENT '被推广小程序用户ID',
  `invite_code` varchar(32) NOT NULL COMMENT '绑定时邀请码快照',
  `first_visit_time` datetime DEFAULT NULL COMMENT '首次扫码时间',
  `bind_time` datetime NOT NULL COMMENT '永久绑定时间',
  `existing_user` tinyint NOT NULL DEFAULT '0' COMMENT '绑定时是否为老用户(0否/1是)',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '关系状态ACTIVE',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`relation_id`),
  UNIQUE KEY `uk_distribution_relation_user` (`referred_user_id`),
  KEY `idx_distribution_relation_partner_bind` (`partner_id`,`bind_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='一级分销永久关系';
/*!40101 SET character_set_client = @saved_cs_client */;


--
-- Table structure for table `distribution_visit`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `distribution_visit` (
  `visit_id` bigint NOT NULL AUTO_INCREMENT COMMENT '访问ID',
  `event_id` varchar(64) NOT NULL COMMENT '客户端访问事件UUID',
  `partner_id` bigint NOT NULL COMMENT '分销商ID',
  `user_id` bigint DEFAULT NULL COMMENT '登录后回填的小程序用户ID',
  `launch_scene` varchar(64) DEFAULT '' COMMENT '微信启动场景',
  `bound` tinyint NOT NULL DEFAULT '0' COMMENT '是否形成有效绑定(0否/1是)',
  `visit_time` datetime NOT NULL COMMENT '访问时间',
  `bind_time` datetime DEFAULT NULL COMMENT '绑定时间',
  PRIMARY KEY (`visit_id`),
  UNIQUE KEY `uk_distribution_visit_event` (`event_id`),
  KEY `idx_distribution_visit_partner_time` (`partner_id`,`visit_time`),
  KEY `idx_distribution_visit_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='分销二维码访问事件';
/*!40101 SET character_set_client = @saved_cs_client */;


--
-- Table structure for table `distribution_withdrawal`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `distribution_withdrawal` (
  `withdrawal_id` bigint NOT NULL AUTO_INCREMENT COMMENT '提现ID',
  `withdrawal_no` varchar(64) NOT NULL COMMENT '提现单号',
  `partner_id` bigint NOT NULL COMMENT '分销商ID',
  `apply_amount` bigint NOT NULL COMMENT '申请金额(分)',
  `method_type` varchar(16) NOT NULL COMMENT 'WECHAT_QR/ALIPAY_QR/BANK_CARD',
  `payee_name` varchar(64) NOT NULL COMMENT '收款人姓名',
  `bank_name` varchar(128) DEFAULT NULL COMMENT '银行名称',
  `bank_account_cipher` varchar(1024) DEFAULT NULL COMMENT '银行卡号密文',
  `bank_account_masked` varchar(64) DEFAULT NULL COMMENT '银行卡号脱敏值',
  `receipt_qr_file_key` varchar(255) DEFAULT NULL COMMENT '私有收款码文件键',
  `status` varchar(16) NOT NULL DEFAULT 'SUBMITTED' COMMENT 'SUBMITTED/NEEDS_REVIEW/PAID/REJECTED',
  `reject_reason` varchar(500) DEFAULT NULL COMMENT '驳回原因',
  `payment_reference` varchar(128) DEFAULT NULL COMMENT '后台打款流水号',
  `payment_proof_file_key` varchar(255) DEFAULT NULL COMMENT '私有付款凭证文件键',
  `apply_time` datetime NOT NULL COMMENT '申请时间',
  `audit_time` datetime DEFAULT NULL COMMENT '审核时间',
  `paid_time` datetime DEFAULT NULL COMMENT '打款时间',
  `audit_by` varchar(64) DEFAULT NULL COMMENT '审核管理员',
  `pay_by` varchar(64) DEFAULT NULL COMMENT '打款管理员',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`withdrawal_id`),
  UNIQUE KEY `uk_distribution_withdrawal_no` (`withdrawal_no`),
  KEY `idx_distribution_withdrawal_partner_time` (`partner_id`,`apply_time`),
  KEY `idx_distribution_withdrawal_status_time` (`status`,`apply_time`),
  CONSTRAINT `chk_distribution_withdrawal_amount` CHECK ((`apply_amount` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='分销提现单';
/*!40101 SET character_set_client = @saved_cs_client */;


--
-- Table structure for table `distribution_withdrawal_allocation`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `distribution_withdrawal_allocation` (
  `allocation_id` bigint NOT NULL AUTO_INCREMENT COMMENT '提现分配ID',
  `withdrawal_id` bigint NOT NULL COMMENT '提现ID',
  `commission_id` bigint NOT NULL COMMENT '佣金ID',
  `allocated_amount` bigint NOT NULL COMMENT '本次锁定金额(分)',
  `status` varchar(16) NOT NULL DEFAULT 'LOCKED' COMMENT 'LOCKED/RESTORED/PAID/REVERSED',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`allocation_id`),
  UNIQUE KEY `uk_distribution_allocation_pair` (`withdrawal_id`,`commission_id`),
  KEY `idx_distribution_allocation_commission` (`commission_id`,`status`),
  CONSTRAINT `chk_distribution_allocation_amount` CHECK ((`allocated_amount` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='提现佣金分配明细';
/*!40101 SET character_set_client = @saved_cs_client */;


--
-- Table structure for table `flyway_schema_history`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flyway_schema_history` (
  `installed_rank` int NOT NULL,
  `version` varchar(50) DEFAULT NULL,
  `description` varchar(200) NOT NULL,
  `type` varchar(20) NOT NULL,
  `script` varchar(1000) NOT NULL,
  `checksum` int DEFAULT NULL,
  `installed_by` varchar(100) NOT NULL,
  `installed_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `execution_time` int NOT NULL,
  `success` tinyint(1) NOT NULL,
  PRIMARY KEY (`installed_rank`),
  KEY `flyway_schema_history_s_idx` (`success`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `flyway_schema_history`
--

INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (1,'1','<< Flyway Baseline >>','BASELINE','<< Flyway Baseline >>',NULL,'root','2026-10-13 03:01:15',0,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (2,'2','mobile wechat','SQL','V2__mobile_wechat.sql',-486408127,'root','2026-10-13 03:01:15',24,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (3,'3','mobile menu','SQL','V3__mobile_menu.sql',50864944,'root','2026-10-13 03:01:15',3,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (4,'4','mobile menu adjust','SQL','V4__mobile_menu_adjust.sql',-629851608,'root','2026-10-13 03:01:15',2,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (5,'5','mobile user no','SQL','V5__mobile_user_no.sql',1314621592,'root','2026-10-13 03:01:15',21,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (6,'6','mobile tabbar','SQL','V6__mobile_tabbar.sql',205619435,'root','2026-10-13 03:01:15',9,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (7,'7','mobile bean','SQL','V7__mobile_bean.sql',-565872470,'root','2026-10-13 03:01:15',12,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (8,'8','mobile membership','SQL','V8__mobile_membership.sql',-575786633,'root','2026-10-13 03:01:15',9,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (9,'9','mobile template','SQL','V9__mobile_template.sql',-303165141,'root','2026-10-13 03:01:15',8,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (10,'10','mobile skill prompt','SQL','V10__mobile_skill_prompt.sql',-548784736,'root','2026-10-13 03:01:15',8,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (11,'11','ai agent loop','SQL','V11__ai_agent_loop.sql',-2008596781,'root','2026-10-13 03:01:15',15,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (12,'12','ai model billing','SQL','V12__ai_model_billing.sql',-1278558693,'root','2026-10-13 03:01:15',19,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (13,'13','ai model billing int','SQL','V13__ai_model_billing_int.sql',439100229,'root','2026-10-13 03:01:15',12,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (14,'14','audit record','SQL','V14__audit_record.sql',421672374,'root','2026-10-13 03:01:15',5,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (15,'15','membership card bg','SQL','V15__membership_card_bg.sql',1632671691,'root','2026-10-13 03:01:15',5,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (16,'16','ai generation img2img','SQL','V16__ai_generation_img2img.sql',496132310,'root','2026-10-13 03:01:15',13,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (17,'17','ai model default','SQL','V17__ai_model_default.sql',4203022,'root','2026-10-13 03:01:15',37,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (18,'18','template reference images','SQL','V18__template_reference_images.sql',49800826,'root','2026-10-13 03:01:15',7,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (19,'19','template featured','SQL','V19__template_featured.sql',904823457,'root','2026-10-13 03:01:15',4,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (20,'20','sys menu cleanup','SQL','V20__sys_menu_cleanup.sql',-618058567,'root','2026-10-13 03:01:15',3,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (21,'21','pay order product type','SQL','V21__pay_order_product_type.sql',-1093488527,'root','2026-10-13 03:01:15',5,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (22,'22','membership level duration','SQL','V22__membership_level_duration.sql',-1673778769,'root','2026-10-13 03:01:15',5,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (23,'23','bean recharge package','SQL','V23__bean_recharge_package.sql',-1677185575,'root','2026-10-13 03:01:15',3,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (24,'24','sys wx config publickey','SQL','V24__sys_wx_config_publickey.sql',1649495908,'root','2026-10-13 03:01:15',4,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (25,'25','pay order menu','SQL','V25__pay_order_menu.sql',236846370,'root','2026-10-13 03:01:15',1,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (26,'26','beanpackage query perm','SQL','V26__beanpackage_query_perm.sql',1420016408,'root','2026-10-13 03:01:15',0,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (27,'27','mobile share config','SQL','V27__mobile_share_config.sql',-415029141,'root','2026-10-13 03:01:15',0,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (28,'28','wx config virtual pay','SQL','V28__wx_config_virtual_pay.sql',921389166,'root','2026-10-13 03:01:15',6,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (29,'29','global prompt into settings','SQL','V29__global_prompt_into_settings.sql',605552554,'root','2026-10-13 03:01:15',0,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (30,'30','drop graph knowledge','SQL','V30__drop_graph_knowledge.sql',-808228844,'root','2026-10-13 03:01:15',22,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (31,'31','drop embedding model','SQL','V31__drop_embedding_model.sql',-134708435,'root','2026-10-13 03:01:15',1,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (32,'32','system settings permissions','SQL','V32__system_settings_permissions.sql',-1668140167,'root','2026-10-13 03:01:15',1,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (33,'33','virtual pay foundation','SQL','V33__virtual_pay_foundation.sql',1643306909,'root','2026-10-13 03:01:15',22,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (34,'34','membership expiry cleanup','SQL','V34__membership_expiry_cleanup.sql',1518440978,'root','2026-10-13 03:01:16',23,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (35,'35','generation task request items','SQL','V35__generation_task_request_items.sql',2009358339,'root','2026-10-13 03:01:16',6,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (36,'36','gpt 5 6 sol agent model','SQL','V36__gpt_5_6_sol_agent_model.sql',1219719511,'root','2026-10-13 03:01:16',0,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (37,'37','ai model api protocol','SQL','V37__ai_model_api_protocol.sql',-1267675108,'root','2026-10-13 03:01:16',3,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (38,'38','ai model resolution billing','SQL','V38__ai_model_resolution_billing.sql',1855886626,'root','2026-10-13 03:01:16',9,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (39,'39','agent message attachment scope','SQL','V39__agent_message_attachment_scope.sql',-36686043,'root','2026-10-13 03:01:16',8,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (40,'40','generation task prompt capacity','SQL','V40__generation_task_prompt_capacity.sql',-2065033213,'root','2026-10-13 03:01:16',7,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (41,'41','agent clarification context','SQL','V41__agent_clarification_context.sql',691561761,'root','2026-10-13 03:01:16',9,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (42,'42','generation task message recovery','SQL','V42__generation_task_message_recovery.sql',-757018821,'root','2026-10-13 03:01:16',3,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (43,'43','template industry category','SQL','V43__template_industry_category.sql',933820629,'root','2026-10-13 03:01:16',12,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (44,'44','membership 365 and permanent recharge','SQL','V44__membership_365_and_permanent_recharge.sql',-323113315,'root','2026-10-13 03:01:16',9,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (45,'45','distribution management','SQL','V45__distribution_management.sql',-202897844,'root','2026-10-13 03:01:16',26,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (46,'46','generation feature settings','SQL','V46__generation_feature_settings.sql',1950436044,'root','2026-10-13 03:01:16',2,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (47,'47','membership editable duration','SQL','V47__membership_editable_duration.sql',1944962496,'root','2026-10-13 03:01:16',7,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (48,'48','prompt favorite','SQL','V48__prompt_favorite.sql',59391234,'root','2026-10-13 03:01:16',4,1);
INSERT INTO `flyway_schema_history` (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`) VALUES (49,'49','message history cursor index','SQL','V49__message_history_cursor_index.sql',245698987,'root','2026-10-13 03:01:16',3,1);

--
-- Table structure for table `generation_task`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `generation_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  `task_id` varchar(64) NOT NULL COMMENT 'Redis 队列 taskId(UUID 去横线),唯一',
  `user_id` bigint NOT NULL COMMENT 'mobile_user.id',
  `conversation_id` bigint DEFAULT NULL COMMENT '对话内生图来源会话',
  `message_id` bigint DEFAULT NULL COMMENT '触发任务的用户消息ID(独立生成页为空)',
  `task_type` varchar(20) DEFAULT 'image' COMMENT '任务类型(本期固定 image,预留 video)',
  `model_id` bigint NOT NULL COMMENT '出图模型 id(yunwu/gptsapi)',
  `prompt` mediumtext NOT NULL COMMENT '该任务提示词，最多32000个Unicode字符',
  `n` int NOT NULL DEFAULT '1' COMMENT '张数(1~6)',
  `resolution` varchar(20) DEFAULT NULL COMMENT '空走模型 default_resolution',
  `aspect_ratio` varchar(20) DEFAULT NULL COMMENT '空走默认',
  `ref_images` text COMMENT 'JSON 数组:图生图参考图 /profile URL(1~4);空=文生图',
  `request_items` longtext COMMENT '批量生图任务项 JSON；空沿用 prompt+n',
  `status` varchar(20) NOT NULL DEFAULT 'CREATED' COMMENT '状态(CREATED/QUEUED/RUNNING/SUCCEEDED/FAILED)',
  `result_urls` text COMMENT 'JSON 数组:成功的 /profile/*.png URL',
  `success_count` int DEFAULT '0' COMMENT '实际成功张数',
  `error_msg` varchar(500) DEFAULT NULL COMMENT '失败原因(脱敏)',
  `bean_cost` int DEFAULT NULL COMMENT '预留计费,本期 null',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task` (`task_id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_conv` (`conversation_id`),
  KEY `idx_status` (`status`),
  KEY `idx_message_recovery` (`user_id`,`conversation_id`,`message_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 闭环出图异步任务';
/*!40101 SET character_set_client = @saved_cs_client */;


--
-- Table structure for table `global_prompt`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `global_prompt` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `content` text COMMENT '提示词内容(送大模型原始文本,入库前 htmlUnescape)',
  `is_active` char(1) NOT NULL DEFAULT '0' COMMENT '是否生效(1生效/0历史),全局唯一仅一条为1',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `del_flag` char(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除标记(0存在/2删除)',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='移动端全局提示词表(全局唯一生效 + 历史版本)';
/*!40101 SET character_set_client = @saved_cs_client */;


--
-- Table structure for table `membership_level`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `membership_level` (
  `level_id` bigint NOT NULL AUTO_INCREMENT COMMENT '等级ID',
  `name` varchar(64) NOT NULL COMMENT '等级名称',
  `grant_beans` bigint NOT NULL DEFAULT '0' COMMENT '每周期送豆数',
  `price` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '价格(单位:元,仅展示/记录,本期无支付)',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序序号(越大等级越高)',
  `status` char(1) NOT NULL DEFAULT '0' COMMENT '状态(0启用/1停用)',
  `card_bg` varchar(255) DEFAULT NULL COMMENT '会员卡片背景图URL',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `del_flag` char(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除标记(0存在/2删除)',
  `duration_days` int NOT NULL DEFAULT '365' COMMENT '会员时长(天)',
  `virtual_product_id` varchar(20) DEFAULT NULL COMMENT '微信虚拟支付普通道具ID',
  PRIMARY KEY (`level_id`),
  UNIQUE KEY `uk_membership_virtual_product` (`virtual_product_id`),
  CONSTRAINT `chk_membership_duration_range` CHECK ((`duration_days` between 1 and 3650))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='移动端会员等级配置表';
/*!40101 SET character_set_client = @saved_cs_client */;


--
-- Table structure for table `message`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '消息ID',
  `conversation_id` bigint NOT NULL COMMENT '所属会话ID',
  `user_id` bigint NOT NULL COMMENT '冗余 user_id(便于按人查/清理)',
  `role` varchar(20) NOT NULL COMMENT '角色(user/assistant/tool)',
  `content` longtext COMMENT '文本;assistant 结构化产出存其 JSON 串',
  `content_type` varchar(20) DEFAULT 'text' COMMENT '内容类型(text/structured)',
  `media` text COMMENT 'JSON:用户上传图 URL 列表 或 产出 images 引用 [{taskId,seq}]',
  `tool_name` varchar(64) DEFAULT NULL COMMENT 'role=tool 时:recognize_image / generate_image',
  `tool_call_id` varchar(64) DEFAULT NULL COMMENT '工具调用关联 id(供审计/回灌)',
  `token_usage` int DEFAULT NULL COMMENT '预留计费,本期 null',
  `bean_cost` int DEFAULT NULL COMMENT '预留计费,本期 null',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_conv` (`conversation_id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_conversation_id` (`conversation_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 闭环对话消息明细';
/*!40101 SET character_set_client = @saved_cs_client */;


--
-- Table structure for table `message_attachment`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `message_attachment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `attachment_id` varchar(64) NOT NULL COMMENT '对话内稳定附件ID',
  `message_id` bigint NOT NULL COMMENT '所属用户消息ID',
  `conversation_id` bigint NOT NULL COMMENT '所属会话ID',
  `user_id` bigint NOT NULL COMMENT '所属用户ID',
  `position` int NOT NULL COMMENT '原消息内顺序，从1开始',
  `source_type` varchar(20) NOT NULL COMMENT '来源：UPLOAD/GENERATED',
  `profile_url` text NOT NULL COMMENT '实际可读图片地址',
  `source_task_id` varchar(64) DEFAULT NULL COMMENT '来源生成任务ID',
  `source_seq` int DEFAULT NULL COMMENT '来源生成结果序号',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_attachment_id` (`attachment_id`),
  UNIQUE KEY `uk_message_position` (`message_id`,`position`),
  KEY `idx_conversation` (`conversation_id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_source_task` (`source_task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Agent用户消息图片附件';
/*!40101 SET character_set_client = @saved_cs_client */;


--
-- Table structure for table `mobile_user`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mobile_user` (
  `user_id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `user_no` varchar(32) DEFAULT NULL COMMENT '用户编号(Ts+yyMMddHH+4随机+4数字)',
  `wx_openid` varchar(64) DEFAULT NULL COMMENT '微信小程序 openid',
  `wx_unionid` varchar(64) DEFAULT NULL COMMENT '微信 unionid(同主体多端唯一)',
  `wx_session_key` varchar(512) DEFAULT NULL COMMENT '微信 session_key(AES 加密存储)',
  `session_key_updated` datetime DEFAULT NULL COMMENT '微信 session_key 最近刷新时间',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
  `password` varchar(100) DEFAULT NULL COMMENT '密码(加密存储)',
  `nick_name` varchar(50) DEFAULT NULL COMMENT '昵称',
  `avatar_url` varchar(500) DEFAULT NULL COMMENT '头像URL',
  `gender` tinyint DEFAULT NULL COMMENT '性别(0未知/1男/2女)',
  `source` varchar(20) DEFAULT NULL COMMENT '来源渠道',
  `status` varchar(1) DEFAULT '0' COMMENT '状态(0正常/1停用)',
  `del_flag` char(1) DEFAULT '0' COMMENT '逻辑删除标记(0存在/2删除)',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `uk_openid` (`wx_openid`),
  UNIQUE KEY `uk_user_no` (`user_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='移动端用户表';
/*!40101 SET character_set_client = @saved_cs_client */;


--
-- Table structure for table `pay_order`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pay_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_no` varchar(64) NOT NULL COMMENT '商户订单号',
  `user_id` bigint DEFAULT NULL COMMENT '下单用户ID',
  `open_id` varchar(64) DEFAULT NULL COMMENT '支付用户 openid',
  `amount` int NOT NULL COMMENT '金额(单位:分)',
  `subject` varchar(255) DEFAULT NULL COMMENT '商品描述',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态(0待支付/1已支付/2已退款/3已关闭/4已支付待退款)',
  `transaction_id` varchar(64) DEFAULT NULL COMMENT '微信支付交易单号',
  `pay_time` datetime DEFAULT NULL COMMENT '支付完成时间',
  `refund_no` varchar(64) DEFAULT NULL COMMENT '退款单号',
  `refund_amount` int DEFAULT NULL COMMENT '退款金额(单位:分)',
  `refund_time` datetime DEFAULT NULL COMMENT '退款时间',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `product_type` varchar(16) DEFAULT NULL COMMENT '订单类型MEMBERSHIP/BEAN_RECHARGE',
  `product_id` bigint DEFAULT NULL COMMENT '关联业务id(会员等级id/套餐id)',
  `product_name` varchar(255) DEFAULT NULL COMMENT '商品名称快照',
  `pay_channel` varchar(16) NOT NULL DEFAULT 'WXPAY' COMMENT '支付渠道(WXPAY/VIRTUAL)',
  `wx_order_id` varchar(64) DEFAULT NULL COMMENT '微信虚拟支付订单号',
  `env` tinyint DEFAULT NULL COMMENT '虚拟支付环境(0现网/1沙箱)',
  `virtual_product_id` varchar(20) DEFAULT NULL COMMENT '微信普通道具ID快照',
  `snapshot_beans` bigint DEFAULT NULL COMMENT '豆子套餐到账豆数快照',
  `snapshot_duration_days` int DEFAULT NULL COMMENT '会员有效天数快照',
  `snapshot_grant_beans` bigint DEFAULT NULL COMMENT '会员赠豆数快照',
  `snapshot_level_sort` int DEFAULT NULL COMMENT '会员等级排序快照',
  `fulfillment_status` tinyint DEFAULT NULL COMMENT '履约状态(0待发放/1已发放/2失败)',
  `fulfillment_error` varchar(500) DEFAULT NULL COMMENT '最近履约失败原因',
  `fulfilled_at` datetime DEFAULT NULL COMMENT '履约完成时间',
  `delivery_confirmed` tinyint NOT NULL DEFAULT '0' COMMENT '微信侧发货确认(0待确认/1已确认)',
  `delivery_confirmed_at` datetime DEFAULT NULL COMMENT '微信侧发货确认时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  UNIQUE KEY `uk_pay_order_wx_order_id` (`wx_order_id`),
  KEY `idx_pay_order_channel_status` (`pay_channel`,`status`,`create_time`),
  KEY `idx_pay_order_fulfillment` (`fulfillment_status`,`create_time`),
  KEY `idx_pay_order_delivery_confirmed` (`delivery_confirmed`,`create_time`),
  KEY `idx_pay_order_virtual_product` (`virtual_product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='支付订单表';
/*!40101 SET character_set_client = @saved_cs_client */;


--
-- Table structure for table `prompt_favorite`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `prompt_favorite` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '收藏ID',
  `user_id` bigint NOT NULL COMMENT '移动端用户ID',
  `name` varchar(64) NOT NULL COMMENT '收藏备注名称',
  `content` mediumtext NOT NULL COMMENT '提示词原文',
  `content_hash` char(64) NOT NULL COMMENT '规范化提示词SHA-256',
  `create_by` varchar(64) NOT NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) NOT NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `del_flag` char(1) NOT NULL DEFAULT '0' COMMENT '删除标志（0存在 2删除）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_prompt_favorite_user_content` (`user_id`,`content_hash`),
  KEY `idx_prompt_favorite_user_status_time` (`user_id`,`del_flag`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户提示词收藏';
/*!40101 SET character_set_client = @saved_cs_client */;


--
-- Table structure for table `reference_scope`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reference_scope` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `scope_id` varchar(64) NOT NULL COMMENT '一次性引用范围ID',
  `conversation_id` bigint NOT NULL COMMENT '所属会话ID',
  `user_id` bigint NOT NULL COMMENT '所属用户ID',
  `source_message_id` bigint NOT NULL COMMENT '原始需求消息ID',
  `attachment_ids` text NOT NULL COMMENT '允许继续使用的附件ID数组',
  `question` text COMMENT '本次澄清问题',
  `allow_additional_images` tinyint NOT NULL DEFAULT '0' COMMENT '下一轮是否允许追加新图',
  `context_json` mediumtext COMMENT '已问问题、答案和选项的紧凑上下文',
  `round_no` int NOT NULL DEFAULT '1' COMMENT '澄清轮数，从1开始',
  `expires_at` datetime DEFAULT NULL COMMENT '澄清范围过期时间',
  `status` varchar(20) NOT NULL COMMENT '状态：OPEN/CONSUMED/CANCELLED',
  `consumed_by_message_id` bigint DEFAULT NULL COMMENT '消费该范围的用户消息ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scope_id` (`scope_id`),
  KEY `idx_open_scope` (`conversation_id`,`user_id`,`status`,`id`),
  KEY `idx_source_message` (`source_message_id`),
  KEY `idx_scope_expire` (`status`,`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Agent一次性澄清图片引用范围';
/*!40101 SET character_set_client = @saved_cs_client */;


--
-- Table structure for table `skill`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `skill` (
  `skill_id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Skill ID',
  `name` varchar(64) NOT NULL COMMENT 'Skill 名称',
  `icon` varchar(512) DEFAULT NULL COMMENT '图标 URL',
  `description` varchar(255) DEFAULT NULL COMMENT '说明',
  `inject_prompt` text COMMENT '注入提示词(送大模型原始文本,入库前 htmlUnescape)',
  `capabilities` varchar(128) DEFAULT NULL COMMENT '可用能力范围(逗号分隔:text文本/vision视觉/image生图/video生视频)',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序(小在前)',
  `status` char(1) NOT NULL DEFAULT '0' COMMENT '状态(0启用/1停用)',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `del_flag` char(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除标记(0存在/2删除)',
  PRIMARY KEY (`skill_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='移动端 Skill 配置表';
/*!40101 SET character_set_client = @saved_cs_client */;


--
-- Table structure for table `sys_ai_history`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_ai_history` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `session_id` varchar(100) NOT NULL COMMENT 'Conversation Session ID',
  `model_id` bigint DEFAULT NULL COMMENT 'AI Model ID used',
  `model_name` varchar(100) DEFAULT NULL COMMENT 'AI Model Name used',
  `role` varchar(20) NOT NULL COMMENT 'Role: user or assistant',
  `content` longtext COMMENT 'Text content or Image Result',
  `msg_type` varchar(20) DEFAULT 'text' COMMENT 'Message Type: text, image',
  `prompt` text COMMENT 'The original prompt if this is an image result',
  `source_type` varchar(20) DEFAULT 'chat' COMMENT 'Source Type: chat, knowledge, graph',
  `source_id` bigint DEFAULT NULL COMMENT 'Source ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation Time',
  PRIMARY KEY (`id`),
  KEY `idx_session` (`session_id`),
  KEY `idx_source` (`source_type`,`source_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI Conversation and Image History';
/*!40101 SET character_set_client = @saved_cs_client */;


--
-- Table structure for table `sys_ai_model`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_ai_model` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `model_name` varchar(100) NOT NULL COMMENT 'Display Name',
  `api_key` varchar(500) NOT NULL COMMENT 'Encrypted API Key',
  `secret_key` varchar(500) DEFAULT NULL COMMENT '加密的SecretKey(仅可灵等双密钥模型用)',
  `supported_resolutions` varchar(255) DEFAULT NULL COMMENT '允许的画质集合,逗号分隔(仅图像模型)',
  `supported_aspect_ratios` varchar(255) DEFAULT NULL COMMENT '允许的画幅比例集合,逗号分隔(仅图像模型)',
  `default_resolution` varchar(20) DEFAULT NULL COMMENT '默认分辨率,仅图像模型(移动端不传分辨率时用)',
  `bean_per_mtoken_input` bigint DEFAULT NULL COMMENT '文本-每百万token输入豆子单价(缓存未命中)',
  `bean_per_mtoken_input_cached` bigint DEFAULT NULL COMMENT '文本-每百万token输入豆子单价(缓存命中,仅 deepseek 填)',
  `bean_per_mtoken_output` bigint DEFAULT NULL COMMENT '文本-每百万token输出豆子单价',
  `bean_per_image` bigint DEFAULT NULL COMMENT '图片-每张豆子单价',
  `bean_per_image_1k` decimal(12,4) DEFAULT NULL COMMENT '图片-1K每张豆子单价',
  `bean_per_image_2k` decimal(12,4) DEFAULT NULL COMMENT '图片-2K每张豆子单价',
  `bean_per_image_4k` decimal(12,4) DEFAULT NULL COMMENT '图片-4K每张豆子单价',
  `bean_per_second` bigint DEFAULT NULL COMMENT '视频-每秒豆子单价(本期仅配置不实扣)',
  `base_url` varchar(255) DEFAULT 'https://api.openai.com' COMMENT 'Base URL',
  `model_deployment` varchar(100) NOT NULL COMMENT 'Model Deployment Name (e.g. gpt-4)',
  `temperature` double DEFAULT '0.7' COMMENT 'Temperature',
  `max_tokens` int DEFAULT '2000' COMMENT 'Max Tokens',
  `provider` varchar(50) DEFAULT 'openai' COMMENT 'Provider Type',
  `model_type` varchar(20) NOT NULL DEFAULT 'chat' COMMENT 'Model type (chat, vision, embedding, image, audio)',
  `api_protocol` varchar(32) NOT NULL DEFAULT 'chat_completions' COMMENT '接口协议：chat_completions/responses',
  `is_active` tinyint(1) DEFAULT '1' COMMENT 'Is Active',
  `is_default` tinyint(1) DEFAULT '0' COMMENT '是否默认(同 model_type 唯一,1默认/0否)',
  `sort_order` int DEFAULT '0' COMMENT 'Sort Order',
  `create_time` datetime DEFAULT NULL COMMENT 'Create Time',
  `update_time` datetime DEFAULT NULL COMMENT 'Update Time',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='System AI Model Configuration';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_ai_model`
--

INSERT INTO `sys_ai_model` (`id`, `model_name`, `api_key`, `secret_key`, `supported_resolutions`, `supported_aspect_ratios`, `default_resolution`, `bean_per_mtoken_input`, `bean_per_mtoken_input_cached`, `bean_per_mtoken_output`, `bean_per_image`, `bean_per_image_1k`, `bean_per_image_2k`, `bean_per_image_4k`, `bean_per_second`, `base_url`, `model_deployment`, `temperature`, `max_tokens`, `provider`, `model_type`, `api_protocol`, `is_active`, `is_default`, `sort_order`, `create_time`, `update_time`) VALUES (1,'DeepSeek V4 Pro','',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'https://api.deepseek.com','deepseek-v4-pro',0.7,2000,'deepseek','chat','chat_completions',1,0,1,'2025-12-22 05:15:14','2026-06-17 23:54:56');
INSERT INTO `sys_ai_model` (`id`, `model_name`, `api_key`, `secret_key`, `supported_resolutions`, `supported_aspect_ratios`, `default_resolution`, `bean_per_mtoken_input`, `bean_per_mtoken_input_cached`, `bean_per_mtoken_output`, `bean_per_image`, `bean_per_image_1k`, `bean_per_image_2k`, `bean_per_image_4k`, `bean_per_second`, `base_url`, `model_deployment`, `temperature`, `max_tokens`, `provider`, `model_type`, `api_protocol`, `is_active`, `is_default`, `sort_order`, `create_time`, `update_time`) VALUES (12,'DeepSeek Chat(Agent)','',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'https://api.deepseek.com','deepseek-chat',0.7,2000,'deepseek','chat','chat_completions',1,0,0,'2026-10-12 23:01:15','2026-10-12 23:01:15');
INSERT INTO `sys_ai_model` (`id`, `model_name`, `api_key`, `secret_key`, `supported_resolutions`, `supported_aspect_ratios`, `default_resolution`, `bean_per_mtoken_input`, `bean_per_mtoken_input_cached`, `bean_per_mtoken_output`, `bean_per_image`, `bean_per_image_1k`, `bean_per_image_2k`, `bean_per_image_4k`, `bean_per_second`, `base_url`, `model_deployment`, `temperature`, `max_tokens`, `provider`, `model_type`, `api_protocol`, `is_active`, `is_default`, `sort_order`, `create_time`, `update_time`) VALUES (13,'Gemini 视觉','',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'https://api.deepseek.com','gemini-2.5-flash',0.7,2000,'yunwu','vision','chat_completions',1,0,0,'2026-10-12 23:01:15','2026-10-12 23:01:15');

--
-- Table structure for table `sys_config`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_config` (
  `config_id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Parameter ID',
  `config_name` varchar(100) DEFAULT '' COMMENT 'Parameter Name',
  `config_key` varchar(100) DEFAULT '' COMMENT 'Parameter Key',
  `config_value` varchar(500) DEFAULT '' COMMENT 'Parameter Value',
  `config_type` char(1) DEFAULT 'N' COMMENT 'Built-in (Y yes, N no)',
  `create_by` varchar(64) DEFAULT '' COMMENT 'Created by',
  `create_time` datetime DEFAULT NULL COMMENT 'Create time',
  `update_by` varchar(64) DEFAULT '' COMMENT 'Updated by',
  `update_time` datetime DEFAULT NULL COMMENT 'Update time',
  `remark` varchar(500) DEFAULT NULL COMMENT 'Remark',
  PRIMARY KEY (`config_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Parameter configuration table';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_config`
--

INSERT INTO `sys_config` (`config_id`, `config_name`, `config_key`, `config_value`, `config_type`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (1,'系统名称','sys.name','Aurora Scaffold','Y','admin','2025-12-22 11:43:54','','2025-12-22 20:32:04','系统显示的名称');
INSERT INTO `sys_config` (`config_id`, `config_name`, `config_key`, `config_value`, `config_type`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2,'默认密码','sys.user.initPassword','123456','Y','admin','2025-12-22 11:43:54','','2025-12-22 20:32:27','新用户默认密码');
INSERT INTO `sys_config` (`config_id`, `config_name`, `config_key`, `config_value`, `config_type`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (3,'验证码开关','sys.account.captchaEnabled','true','Y','admin','2025-12-22 11:43:54','','2025-12-22 20:32:40','登陆时验证码开关');
INSERT INTO `sys_config` (`config_id`, `config_name`, `config_key`, `config_value`, `config_type`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (4,'用户注册功能','sys.account.registerEnabled','false','Y','admin','2025-12-22 11:43:54','','2025-12-22 20:32:50','是否开启注册功能');
INSERT INTO `sys_config` (`config_id`, `config_name`, `config_key`, `config_value`, `config_type`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (100,'平台名称','sys.index.title','Aboci','Y','admin','2025-12-22 11:57:44','admin','2026-10-12 23:00:46','后台管理系统标题');
INSERT INTO `sys_config` (`config_id`, `config_name`, `config_key`, `config_value`, `config_type`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (101,'平台Logo','sys.index.logo','/logo_abopi_mark.png','Y','admin','2025-12-22 11:57:44','admin','2026-10-12 23:00:46','Logo图片的URL或路径');
INSERT INTO `sys_config` (`config_id`, `config_name`, `config_key`, `config_value`, `config_type`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (102,'暗黑模式底色','sys.theme.dark.bg','#17171a','Y','','2025-12-22 20:21:31','','2026-01-03 23:07:21','暗黑模式下内容区底色');
INSERT INTO `sys_config` (`config_id`, `config_name`, `config_key`, `config_value`, `config_type`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (103,'亮色模式底色','sys.theme.light.bg','#F2F3F5','Y','','2025-12-22 20:21:58','','2025-12-29 20:03:04','亮色模式下内容区底色');
INSERT INTO `sys_config` (`config_id`, `config_name`, `config_key`, `config_value`, `config_type`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (114,'新用户赠送豆数','mobile.bean.new_user_gift','50','N','admin','2026-10-12 23:01:15','',NULL,'移动端新用户注册赠送的豆子数量');
INSERT INTO `sys_config` (`config_id`, `config_name`, `config_key`, `config_value`, `config_type`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (115,'豆子余额预警阈值','mobile.bean.warn_threshold','20','N','admin','2026-10-12 23:01:15','',NULL,'移动端豆子余额低于该值时预警');
INSERT INTO `sys_config` (`config_id`, `config_name`, `config_key`, `config_value`, `config_type`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (116,'小程序分享标题','mobile.share.title','啊播吡 AI 创作','Y','admin','2026-10-12 23:01:15','',NULL,'分享到微信会话/朋友圈的卡片标题');
INSERT INTO `sys_config` (`config_id`, `config_name`, `config_key`, `config_value`, `config_type`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (117,'小程序分享卡片图','mobile.share.image','','Y','admin','2026-10-12 23:01:15','',NULL,'分享卡片图 /profile URL;比例 5:4(建议 500x400);空=微信自动截图');
INSERT INTO `sys_config` (`config_id`, `config_name`, `config_key`, `config_value`, `config_type`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (118,'AI 生成功能','mobile.generation.enabled','true','Y','admin','2026-10-12 23:01:16','',NULL,'控制小程序独立生成页面及其提交接口');
INSERT INTO `sys_config` (`config_id`, `config_name`, `config_key`, `config_value`, `config_type`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (119,'生成图片下载水印','mobile.generation.download_watermark.enabled','true','Y','admin','2026-10-12 23:01:16','',NULL,'控制保存到相册的生成图片是否包含 AI 标识');

--
-- Table structure for table `sys_dept`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dept` (
  `dept_id` bigint NOT NULL AUTO_INCREMENT COMMENT '部门ID',
  `parent_id` bigint DEFAULT '0' COMMENT '父部门ID',
  `ancestors` varchar(500) DEFAULT '' COMMENT '祖级列表',
  `dept_name` varchar(50) NOT NULL COMMENT '部门名称',
  `order_num` int DEFAULT '0' COMMENT '显示顺序',
  `leader` varchar(20) DEFAULT NULL COMMENT '负责人',
  `phone` varchar(11) DEFAULT NULL COMMENT '联系电话',
  `email` varchar(50) DEFAULT NULL COMMENT '邮箱',
  `status` char(1) DEFAULT '0' COMMENT '部门状态（0正常 1停用）',
  `del_flag` char(1) DEFAULT '0' COMMENT '删除标志（0存在 2删除）',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='部门表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_dept`
--

INSERT INTO `sys_dept` (`dept_id`, `parent_id`, `ancestors`, `dept_name`, `order_num`, `leader`, `phone`, `email`, `status`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES (100,0,'0','极光科技',0,'admin','15888888888','admin@aurora.com','0','0','','2026-01-03 15:23:07','',NULL);

--
-- Table structure for table `sys_dict_data`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dict_data` (
  `dict_data_id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Dictionary data ID',
  `dict_sort` int DEFAULT '0' COMMENT 'Dictionary sort',
  `dict_label` varchar(100) DEFAULT '' COMMENT 'Dictionary label',
  `dict_value` varchar(100) DEFAULT '' COMMENT 'Dictionary value',
  `dict_type` varchar(100) DEFAULT '' COMMENT 'Dictionary type',
  `status` char(1) DEFAULT '0' COMMENT 'Status (0 normal, 1 disabled)',
  `create_by` varchar(64) DEFAULT '' COMMENT 'Created by',
  `create_time` datetime DEFAULT NULL COMMENT 'Create time',
  `update_by` varchar(64) DEFAULT '' COMMENT 'Updated by',
  `update_time` datetime DEFAULT NULL COMMENT 'Update time',
  `remark` varchar(500) DEFAULT NULL COMMENT 'Remark',
  PRIMARY KEY (`dict_data_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Dictionary data table';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_dict_data`
--

INSERT INTO `sys_dict_data` (`dict_data_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (1,1,'Male','0','sys_user_sex','0','admin','2025-12-22 11:27:01','',NULL,'Male');
INSERT INTO `sys_dict_data` (`dict_data_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (2,2,'Female','1','sys_user_sex','0','admin','2025-12-22 11:27:01','',NULL,'Female');
INSERT INTO `sys_dict_data` (`dict_data_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (3,3,'Unknown','2','sys_user_sex','0','admin','2025-12-22 11:27:01','',NULL,'Unknown sex');
INSERT INTO `sys_dict_data` (`dict_data_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (100,1,'文本对话 (Chat)','chat','sys_ai_model_type','0','admin','2025-12-22 11:57:44','',NULL,'');
INSERT INTO `sys_dict_data` (`dict_data_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (101,2,'多模态/视觉 (Vision)','vision','sys_ai_model_type','0','admin','2025-12-22 11:57:44','',NULL,'');
INSERT INTO `sys_dict_data` (`dict_data_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (103,4,'图像生成 (Image)','image','sys_ai_model_type','0','admin','2025-12-22 11:57:44','',NULL,'');
INSERT INTO `sys_dict_data` (`dict_data_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (104,5,'语音处理 (Audio)','audio','sys_ai_model_type','0','admin','2025-12-22 11:57:44','',NULL,'');
INSERT INTO `sys_dict_data` (`dict_data_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (105,1,'OpenAI (Compatible)','openai','sys_ai_provider','0','admin','2025-12-22 11:57:44','',NULL,'');
INSERT INTO `sys_dict_data` (`dict_data_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (107,3,'阿里云 (DashScope)','aliyun','sys_ai_provider','0','admin','2025-12-22 11:57:44','',NULL,'');
INSERT INTO `sys_dict_data` (`dict_data_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (108,4,'火山引擎 (Ark)','volcengine','sys_ai_provider','0','admin','2025-12-22 11:57:44','',NULL,'');
INSERT INTO `sys_dict_data` (`dict_data_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (109,5,'深度求索 (DeepSeek)','deepseek','sys_ai_provider','0','admin','2025-12-24 13:14:38','',NULL,'DeepSeek API');
INSERT INTO `sys_dict_data` (`dict_data_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (110,6,'智谱AI (GLM)','zhipu','sys_ai_provider','0','admin','2025-12-24 13:14:38','',NULL,'智谱AI ChatGLM系列');
INSERT INTO `sys_dict_data` (`dict_data_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (111,7,'硅基流动 (SiliconFlow)','siliconflow','sys_ai_provider','0','admin','2025-12-24 13:14:38','',NULL,'硅基流动云服务');
INSERT INTO `sys_dict_data` (`dict_data_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (113,8,'云雾API (Yunwu)','yunwu','sys_ai_provider','0','admin','2026-10-12 23:00:46','',NULL,'云雾API中转');
INSERT INTO `sys_dict_data` (`dict_data_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (114,9,'GPTS','gptsapi','sys_ai_provider','0','admin','2026-10-12 23:00:46','',NULL,'GPTS API中转');
INSERT INTO `sys_dict_data` (`dict_data_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (115,10,'可灵 (Kling)','kling','sys_ai_provider','0','admin','2026-10-12 23:00:46','',NULL,'可灵视频/图片生成，双密钥(AccessKey/SecretKey)');
INSERT INTO `sys_dict_data` (`dict_data_id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (116,6,'视频生成 (Video)','video','sys_ai_model_type','0','admin','2026-10-12 23:00:46','',NULL,'');

--
-- Table structure for table `sys_dict_type`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dict_type` (
  `dict_id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Dictionary ID',
  `dict_name` varchar(100) DEFAULT '' COMMENT 'Dictionary Name',
  `dict_type` varchar(100) DEFAULT '' COMMENT 'Dictionary Type',
  `status` char(1) DEFAULT '0' COMMENT 'Status (0 normal, 1 disabled)',
  `create_by` varchar(64) DEFAULT '' COMMENT 'Created by',
  `create_time` datetime DEFAULT NULL COMMENT 'Create time',
  `update_by` varchar(64) DEFAULT '' COMMENT 'Updated by',
  `update_time` datetime DEFAULT NULL COMMENT 'Update time',
  `remark` varchar(500) DEFAULT NULL COMMENT 'Remark',
  PRIMARY KEY (`dict_id`),
  UNIQUE KEY `dict_type` (`dict_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Dictionary type table';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_dict_type`
--

INSERT INTO `sys_dict_type` (`dict_id`, `dict_name`, `dict_type`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (1,'性别','sys_user_sex','0','admin','2025-12-22 11:27:01','','2025-12-22 19:48:56','User sex dictionary');
INSERT INTO `sys_dict_type` (`dict_id`, `dict_name`, `dict_type`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (101,'AI模型类型','sys_ai_model_type','0','admin','2025-12-22 11:57:44','',NULL,'AI模型功能分类');
INSERT INTO `sys_dict_type` (`dict_id`, `dict_name`, `dict_type`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (102,'AI服务商','sys_ai_provider','0','admin','2025-12-22 11:57:44','',NULL,'AI模型提供商列表');

--
-- Table structure for table `sys_icon`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_icon` (
  `icon_id` bigint NOT NULL AUTO_INCREMENT COMMENT '图标ID',
  `icon_name` varchar(100) NOT NULL COMMENT '图标名称',
  `icon_path` varchar(255) NOT NULL COMMENT '存储路径',
  `icon_type` varchar(10) NOT NULL COMMENT '类型: png/svg',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag` char(1) DEFAULT '0' COMMENT '删除标志（0正常 2已删除）',
  PRIMARY KEY (`icon_id`),
  UNIQUE KEY `uk_icon_name` (`icon_name`,`del_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='自定义图标表';
/*!40101 SET character_set_client = @saved_cs_client */;


--
-- Table structure for table `sys_menu`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_menu` (
  `menu_id` bigint NOT NULL AUTO_INCREMENT COMMENT '菜单ID',
  `menu_name` varchar(50) NOT NULL COMMENT '菜单名称',
  `parent_id` bigint DEFAULT '0' COMMENT '父菜单ID',
  `order_num` int DEFAULT '0' COMMENT '显示顺序',
  `path` varchar(200) DEFAULT '' COMMENT '路由地址',
  `component` varchar(255) DEFAULT NULL COMMENT '组件路径',
  `query` varchar(255) DEFAULT NULL COMMENT '路由参数',
  `is_frame` int DEFAULT '1' COMMENT '是否为外链（0是 1否）',
  `is_cache` int DEFAULT '0' COMMENT '是否缓存（0缓存 1不缓存）',
  `menu_type` char(1) DEFAULT '' COMMENT '菜单类型（M目录 C菜单 F按钮）',
  `visible` char(1) DEFAULT '0' COMMENT '菜单状态（0显示 1隐藏）',
  `status` char(1) DEFAULT '0' COMMENT '菜单状态（0正常 1停用）',
  `perms` varchar(100) DEFAULT NULL COMMENT '权限标识',
  `icon` varchar(100) DEFAULT '#' COMMENT '菜单图标',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT '' COMMENT '备注',
  `del_flag` char(1) DEFAULT '0' COMMENT '删除标志',
  PRIMARY KEY (`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='菜单权限表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_menu`
--

INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (1,'系统管理',0,30,'system',NULL,NULL,1,0,'M','0','0',NULL,'Settings','','2025-12-18 13:21:48','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (100,'用户管理',2011,1,'user','system/user/index',NULL,1,0,'C','0','0','system:user:list','User','','2025-12-18 13:21:48','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (101,'用户查询',100,1,'',NULL,NULL,1,0,'F','0','0','system:user:query','#','','2025-12-18 13:21:48','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (102,'用户新增',100,2,'',NULL,NULL,1,0,'F','0','0','system:user:add','#','','2025-12-18 13:21:48','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (103,'用户修改',100,3,'',NULL,NULL,1,0,'F','0','0','system:user:edit','#','','2025-12-18 13:21:48','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (104,'用户删除',100,4,'',NULL,NULL,1,0,'F','0','0','system:user:remove','#','','2025-12-18 13:21:48','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (105,'用户导出',100,5,'',NULL,NULL,1,0,'F','0','0','system:user:export','#','','2025-12-18 13:21:48','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (106,'用户导入',100,6,'',NULL,NULL,1,0,'F','0','0','system:user:import','#','','2025-12-18 13:21:48','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (200,'角色管理',2011,2,'role','system/role/index',NULL,1,0,'C','0','0','system:role:list','peoples','','2025-12-18 13:21:48','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (201,'角色查询',200,1,'',NULL,NULL,1,0,'F','0','0','system:role:query','#','','2025-12-18 13:21:48','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (202,'角色新增',200,2,'',NULL,NULL,1,0,'F','0','0','system:role:add','#','','2025-12-18 13:21:48','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (203,'角色修改',200,3,'',NULL,NULL,1,0,'F','0','0','system:role:edit','#','','2025-12-18 13:21:48','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (204,'角色删除',200,4,'',NULL,NULL,1,0,'F','0','0','system:role:remove','#','','2025-12-18 13:21:48','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (205,'角色导出',200,5,'',NULL,NULL,1,0,'F','0','0','system:role:export','#','','2025-12-18 13:21:48','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (300,'菜单管理',1,2,'menu','system/menu/index',NULL,1,0,'C','0','0','system:menu:list','File','','2025-12-18 13:21:48','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (301,'菜单查询',300,1,'',NULL,NULL,1,0,'F','0','0','system:menu:query','#','','2025-12-18 13:21:48','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (302,'菜单新增',300,2,'',NULL,NULL,1,0,'F','0','0','system:menu:add','#','','2025-12-18 13:21:48','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (303,'菜单修改',300,3,'',NULL,NULL,1,0,'F','0','0','system:menu:edit','#','','2025-12-18 13:21:48','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (304,'菜单删除',300,4,'',NULL,NULL,1,0,'F','0','0','system:menu:remove','#','','2025-12-18 13:21:48','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2006,'模型管理',1,1,'model','ai/apimanage/index',NULL,1,0,'C','0','0','ai:model:list','Message','admin','2025-12-22 05:22:17','',NULL,'AI Model API Management','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2007,'菜单查询',2006,1,'#','',NULL,1,0,'F','0','0','ai:model:query','#','admin','2025-12-22 05:22:17','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2008,'菜单新增',2006,2,'#','',NULL,1,0,'F','0','0','ai:model:add','#','admin','2025-12-22 05:22:17','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2009,'菜单修改',2006,3,'#','',NULL,1,0,'F','0','0','ai:model:edit','#','admin','2025-12-22 05:22:17','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2010,'菜单删除',2006,4,'#','',NULL,1,0,'F','0','0','ai:model:remove','#','admin','2025-12-22 05:22:17','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2011,'用户管理',0,1,'usersystem','',NULL,1,0,'M','0','0','','User','',NULL,'',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2012,'字典管理',1,5,'dict','system/dict/index',NULL,1,0,'C','0','0','system:dict:list','icon-settings','admin','2025-12-22 11:37:25','',NULL,'字典管理菜单','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2013,'字典查询',2012,1,'#','',NULL,1,0,'F','0','0','system:dict:query','#','admin','2025-12-22 11:37:25','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2014,'字典新增',2012,2,'#','',NULL,1,0,'F','0','0','system:dict:add','#','admin','2025-12-22 11:37:25','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2015,'字典修改',2012,3,'#','',NULL,1,0,'F','0','0','system:dict:edit','#','admin','2025-12-22 11:37:25','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2016,'字典删除',2012,4,'#','',NULL,1,0,'F','0','0','system:dict:remove','#','admin','2025-12-22 11:37:25','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2017,'参数设置',1,6,'config','system/config/index',NULL,1,0,'C','0','0','system:config:list','icon-command','admin','2025-12-22 11:47:24','',NULL,'参数设置菜单','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2018,'参数查询',2017,1,'#','',NULL,1,0,'F','0','0','system:config:query','#','admin','2025-12-22 11:47:24','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2019,'参数新增',2017,2,'#','',NULL,1,0,'F','0','0','system:config:add','#','admin','2025-12-22 11:47:24','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2020,'参数修改',2017,3,'#','',NULL,1,0,'F','0','0','system:config:edit','#','admin','2025-12-22 11:47:24','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2021,'参数删除',2017,4,'#','',NULL,1,0,'F','0','0','system:config:remove','#','admin','2025-12-22 11:47:24','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2022,'运维监控',0,40,'monitor',NULL,NULL,1,0,'M','0','0',NULL,'Dashboard','admin','2025-12-22 13:14:37','',NULL,'运维监控目录','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2023,'服务监控',2022,1,'server','monitor/server/index',NULL,1,0,'C','0','0','monitor:server:list','icon-dashboard','admin','2025-12-22 13:14:37','',NULL,'服务监控菜单','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2049,'操作日志',2022,2,'operlog','monitor/operlog/index',NULL,1,0,'C','0','0','monitor:operlog:list','icon-file','admin','2025-12-24 09:38:12','',NULL,'操作日志菜单','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2050,'操作日志查询',2049,1,'',NULL,NULL,1,0,'F','0','0','monitor:operlog:query','#','admin','2025-12-24 09:38:12','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2051,'操作日志删除',2049,2,'',NULL,NULL,1,0,'F','0','0','monitor:operlog:remove','#','admin','2025-12-24 09:38:12','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2052,'操作日志清空',2049,3,'',NULL,NULL,1,0,'F','0','0','monitor:operlog:clean','#','admin','2025-12-24 09:38:12','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2053,'操作日志导出',2049,4,'',NULL,NULL,1,0,'F','0','0','monitor:operlog:export','#','admin','2025-12-24 09:38:12','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2054,'模版页面',0,101,'/template/base-list','@/views/template/base-list',NULL,1,0,'C','0','0','BaseListTemplate','Home','',NULL,'',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2055,'缓存列表',2022,3,'cache','monitor/cache/index',NULL,1,0,'C','0','0','monitor:cache:list','','',NULL,'',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2101,'部门管理',2011,3,'dept','system/dept/index',NULL,1,0,'C','0','0','system:dept:list','icon-mind-mapping','','2026-01-03 15:22:15','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2102,'部门查询',2101,1,'',NULL,NULL,1,0,'F','0','0','system:dept:query','#','','2026-01-03 15:22:15','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2103,'部门新增',2101,2,'',NULL,NULL,1,0,'F','0','0','system:dept:add','#','','2026-01-03 15:22:15','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2104,'部门修改',2101,3,'',NULL,NULL,1,0,'F','0','0','system:dept:edit','#','','2026-01-03 15:22:15','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2105,'部门删除',2101,4,'',NULL,NULL,1,0,'F','0','0','system:dept:remove','#','','2026-01-03 15:22:15','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2110,'岗位管理',2011,4,'post','system/post/index',NULL,1,0,'C','0','0','system:post:list','icon-bookmark','','2026-01-03 15:22:15','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2111,'岗位查询',2110,1,'',NULL,NULL,1,0,'F','0','0','system:post:query','#','','2026-01-03 15:22:15','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2112,'岗位新增',2110,2,'',NULL,NULL,1,0,'F','0','0','system:post:add','#','','2026-01-03 15:22:15','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2113,'岗位修改',2110,3,'',NULL,NULL,1,0,'F','0','0','system:post:edit','#','','2026-01-03 15:22:15','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2114,'岗位删除',2110,4,'',NULL,NULL,1,0,'F','0','0','system:post:remove','#','','2026-01-03 15:22:15','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2117,'图标列表',2242,0,'',NULL,NULL,1,0,'F','1','0','system:icon:list','#','admin','2026-06-17 23:04:08','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2118,'图标详情',2242,0,'',NULL,NULL,1,0,'F','1','0','system:icon:query','#','admin','2026-06-17 23:04:08','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2119,'图标新增',2242,0,'',NULL,NULL,1,0,'F','1','0','system:icon:add','#','admin','2026-06-17 23:04:08','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2120,'图标修改',2242,0,'',NULL,NULL,1,0,'F','1','0','system:icon:edit','#','admin','2026-06-17 23:04:08','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2121,'图标删除',2242,0,'',NULL,NULL,1,0,'F','1','0','system:icon:remove','#','admin','2026-06-17 23:04:08','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2200,'移动端管理',0,50,'mobile',NULL,NULL,1,0,'M','0','0',NULL,'icon-mobile','admin','2026-10-12 23:01:15','',NULL,'移动端管理目录','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2201,'移动端用户',2200,1,'user','mobile/user/index',NULL,1,0,'C','0','0','mobile:user:list','icon-user','admin','2026-10-12 23:01:15','',NULL,'移动端用户/微信配置一页三卡','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2202,'移动端用户查询',2201,1,'',NULL,NULL,1,0,'F','0','0','mobile:user:list','#','admin','2026-10-12 23:01:15','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2203,'移动端用户启禁用',2201,2,'',NULL,NULL,1,0,'F','0','0','mobile:user:edit','#','admin','2026-10-12 23:01:15','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2204,'系统设置',2200,2,'settings','system/settings/index',NULL,1,0,'C','0','0','system:wxconfig:list','icon-settings','admin','2026-10-12 23:01:15','',NULL,'系统设置(基础配置:微信小程序/支付)','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2205,'微信配置查询',2204,1,'',NULL,NULL,1,0,'F','0','0','system:wxconfig:list','#','admin','2026-10-12 23:01:15','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2206,'微信配置新增',2204,2,'',NULL,NULL,1,0,'F','0','0','system:wxconfig:add','#','admin','2026-10-12 23:01:15','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2207,'微信配置修改',2204,3,'',NULL,NULL,1,0,'F','0','0','system:wxconfig:edit','#','admin','2026-10-12 23:01:15','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2208,'微信配置删除',2204,4,'',NULL,NULL,1,0,'F','0','0','system:wxconfig:remove','#','admin','2026-10-12 23:01:15','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2209,'底部导航',2200,3,'tabbar','mobile/tabbar/index',NULL,1,0,'C','0','0','mobile:tabbar:list','icon-menu','admin','2026-10-12 23:01:15','',NULL,'移动端底部导航配置(名称/双态图标/显隐)','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2210,'底部导航查询',2209,1,'',NULL,NULL,1,0,'F','0','0','mobile:tabbar:list','#','admin','2026-10-12 23:01:15','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2211,'底部导航编辑',2209,2,'',NULL,NULL,1,0,'F','0','0','mobile:tabbar:edit','#','admin','2026-10-12 23:01:15','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2212,'豆子流水查询',2200,4,'',NULL,NULL,1,0,'F','0','0','mobile:bean:list','#','admin','2026-10-12 23:01:15','',NULL,'查看移动端用户豆子流水','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2213,'豆子手动调整',2200,5,'',NULL,NULL,1,0,'F','0','0','mobile:bean:adjust','#','admin','2026-10-12 23:01:15','',NULL,'后台手动增减用户豆子','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2214,'会员管理',0,51,'membership',NULL,NULL,1,0,'M','0','0',NULL,'icon-user-group','admin','2026-10-12 23:01:15','',NULL,'会员管理目录','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2215,'会员等级',2214,1,'level','membership/level/index',NULL,1,0,'C','0','0','membership:level:list','icon-list','admin','2026-10-12 23:01:15','',NULL,'会员等级配置','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2216,'会员等级查询',2215,1,'',NULL,NULL,1,0,'F','0','0','membership:level:list','#','admin','2026-10-12 23:01:15','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2217,'会员等级新增',2215,2,'',NULL,NULL,1,0,'F','0','0','membership:level:add','#','admin','2026-10-12 23:01:15','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2218,'会员等级修改',2215,3,'',NULL,NULL,1,0,'F','0','0','membership:level:edit','#','admin','2026-10-12 23:01:15','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2219,'会员等级删除',2215,4,'',NULL,NULL,1,0,'F','0','0','membership:level:remove','#','admin','2026-10-12 23:01:15','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2220,'会员列表',2214,2,'user','membership/user/index',NULL,1,0,'C','0','0','membership:user:list','icon-user','admin','2026-10-12 23:01:15','',NULL,'会员列表与手动开通','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2221,'会员列表查询',2220,1,'',NULL,NULL,1,0,'F','0','0','membership:user:list','#','admin','2026-10-12 23:01:15','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2222,'会员手动开通',2220,2,'',NULL,NULL,1,0,'F','0','0','membership:user:grant','#','admin','2026-10-12 23:01:15','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2223,'内容管理',0,52,'content',NULL,NULL,1,0,'M','0','0',NULL,'icon-bookmark','admin','2026-10-12 23:01:15','',NULL,'内容管理目录','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2224,'案例/模板',2223,1,'template','template/index',NULL,1,0,'C','0','0','template:list','icon-image','admin','2026-10-12 23:01:15','',NULL,'案例/模板管理','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2225,'案例模板查询',2224,1,'',NULL,NULL,1,0,'F','0','0','template:list','#','admin','2026-10-12 23:01:15','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2226,'案例模板详情',2224,2,'',NULL,NULL,1,0,'F','0','0','template:query','#','admin','2026-10-12 23:01:15','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2227,'案例模板新增',2224,3,'',NULL,NULL,1,0,'F','0','0','template:add','#','admin','2026-10-12 23:01:15','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2228,'案例模板修改',2224,4,'',NULL,NULL,1,0,'F','0','0','template:edit','#','admin','2026-10-12 23:01:15','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2229,'案例模板删除',2224,5,'',NULL,NULL,1,0,'F','0','0','template:remove','#','admin','2026-10-12 23:01:15','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2230,'Skill 配置',2223,2,'skill','skill/index',NULL,1,0,'C','0','0','skill:list','icon-apps','admin','2026-10-12 23:01:15','',NULL,'Skill 配置管理','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2231,'Skill查询',2230,1,'',NULL,NULL,1,0,'F','0','0','skill:list','#','admin','2026-10-12 23:01:15','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2232,'Skill详情',2230,2,'',NULL,NULL,1,0,'F','0','0','skill:query','#','admin','2026-10-12 23:01:15','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2233,'Skill新增',2230,3,'',NULL,NULL,1,0,'F','0','0','skill:add','#','admin','2026-10-12 23:01:15','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2234,'Skill修改',2230,4,'',NULL,NULL,1,0,'F','0','0','skill:edit','#','admin','2026-10-12 23:01:15','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2235,'Skill删除',2230,5,'',NULL,NULL,1,0,'F','0','0','skill:remove','#','admin','2026-10-12 23:01:15','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2236,'全局提示词',2223,3,'prompt','prompt/index',NULL,1,0,'C','1','0','globalprompt:list','icon-edit','admin','2026-10-12 23:01:15','',NULL,'全局提示词管理','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2237,'全局提示词查询',2236,1,'',NULL,NULL,1,0,'F','0','0','globalprompt:list','#','admin','2026-10-12 23:01:15','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2238,'全局提示词编辑',2236,2,'',NULL,NULL,1,0,'F','0','0','globalprompt:edit','#','admin','2026-10-12 23:01:15','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2240,'内容审核日志',2223,4,'audit','ai/audit/index',NULL,1,0,'C','0','0','ai:audit:list','icon-safe','admin','2026-10-12 23:01:15','',NULL,'内容审核日志(送审存证)','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2241,'审核日志查询',2240,1,'',NULL,NULL,1,0,'F','0','0','ai:audit:list','#','admin','2026-10-12 23:01:15','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2242,'图标管理',1,10,'icon','system/icon/index',NULL,1,0,'C','0','0','system:icon:list','icon-image','admin','2026-10-12 23:01:15','',NULL,'图标权限归类(页面待建;system:icon 由 IconPicker 组件调用)','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2243,'豆子套餐',2200,6,'bean-package','mobile/bean-package/index',NULL,1,0,'C','0','0','mobile:beanpackage:list','icon-gift','admin','2026-10-12 23:01:15','',NULL,'豆子充值套餐管理','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2244,'豆子套餐详情',2243,1,'',NULL,NULL,1,0,'F','0','0','mobile:beanpackage:query','#','admin','2026-10-12 23:01:15','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2245,'豆子套餐新增',2243,2,'',NULL,NULL,1,0,'F','0','0','mobile:beanpackage:add','#','admin','2026-10-12 23:01:15','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2246,'豆子套餐修改',2243,3,'',NULL,NULL,1,0,'F','0','0','mobile:beanpackage:edit','#','admin','2026-10-12 23:01:15','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2247,'豆子套餐删除',2243,4,'',NULL,NULL,1,0,'F','0','0','mobile:beanpackage:remove','#','admin','2026-10-12 23:01:15','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2248,'订单管理',2200,7,'pay-order','mobile/pay-order/index',NULL,1,0,'C','0','0','mobile:order:list','icon-file','admin','2026-10-12 23:01:15','',NULL,'支付订单查询','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2249,'订单查询',2248,1,'',NULL,NULL,1,0,'F','0','0','mobile:order:list','#','admin','2026-10-12 23:01:15','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2250,'订单详情',2248,2,'',NULL,NULL,1,0,'F','0','0','mobile:order:query','#','admin','2026-10-12 23:01:15','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2251,'分享配置查询',2204,5,'',NULL,NULL,1,0,'F','0','0','system:shareconfig:list','#','admin','2026-10-12 23:01:15','',NULL,'仅查询 mobile.share.title/image','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2252,'分享配置编辑',2204,6,'',NULL,NULL,1,0,'F','0','0','system:shareconfig:edit','#','admin','2026-10-12 23:01:15','',NULL,'仅修改 mobile.share.title/image','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2260,'行业分类列表',2224,10,'',NULL,NULL,1,0,'F','0','0','template:category:list','#','admin','2026-10-12 23:01:16','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2261,'行业分类详情',2224,11,'',NULL,NULL,1,0,'F','0','0','template:category:query','#','admin','2026-10-12 23:01:16','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2262,'行业分类新增',2224,12,'',NULL,NULL,1,0,'F','0','0','template:category:add','#','admin','2026-10-12 23:01:16','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2263,'行业分类修改',2224,13,'',NULL,NULL,1,0,'F','0','0','template:category:edit','#','admin','2026-10-12 23:01:16','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2264,'行业分类删除',2224,14,'',NULL,NULL,1,0,'F','0','0','template:category:remove','#','admin','2026-10-12 23:01:16','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2270,'分销管理',0,20,'distribution',NULL,NULL,1,0,'M','0','0',NULL,'icon-share-alt','admin','2026-10-12 23:01:16','',NULL,'一级分销管理目录','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2271,'分销概览',2270,1,'overview','distribution/overview/index',NULL,1,0,'C','0','0','distribution:overview:list','icon-dashboard','admin','2026-10-12 23:01:16','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2272,'分销概览查询',2271,1,'',NULL,NULL,1,0,'F','0','0','distribution:overview:list','#','admin','2026-10-12 23:01:16','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2273,'分销商管理',2270,2,'partner','distribution/partner/index',NULL,1,0,'C','0','0','distribution:partner:list','icon-user-group','admin','2026-10-12 23:01:16','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2274,'分销商查询',2273,1,'',NULL,NULL,1,0,'F','0','0','distribution:partner:list','#','admin','2026-10-12 23:01:16','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2275,'分销商详情',2273,2,'',NULL,NULL,1,0,'F','0','0','distribution:partner:query','#','admin','2026-10-12 23:01:16','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2276,'分销商新增',2273,3,'',NULL,NULL,1,0,'F','0','0','distribution:partner:add','#','admin','2026-10-12 23:01:16','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2277,'分销商修改',2273,4,'',NULL,NULL,1,0,'F','0','0','distribution:partner:edit','#','admin','2026-10-12 23:01:16','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2278,'佣金明细',2270,3,'commission','distribution/commission/index',NULL,1,0,'C','0','0','distribution:commission:list','icon-list','admin','2026-10-12 23:01:16','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2279,'佣金列表',2278,1,'',NULL,NULL,1,0,'F','0','0','distribution:commission:list','#','admin','2026-10-12 23:01:16','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2280,'佣金详情',2278,2,'',NULL,NULL,1,0,'F','0','0','distribution:commission:query','#','admin','2026-10-12 23:01:16','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2281,'佣金调整',2278,3,'',NULL,NULL,1,0,'F','0','0','distribution:commission:adjust','#','admin','2026-10-12 23:01:16','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2282,'提现管理',2270,4,'withdrawal','distribution/withdrawal/index',NULL,1,0,'C','0','0','distribution:withdrawal:list','icon-wallet','admin','2026-10-12 23:01:16','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2283,'提现列表',2282,1,'',NULL,NULL,1,0,'F','0','0','distribution:withdrawal:list','#','admin','2026-10-12 23:01:16','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2284,'提现详情',2282,2,'',NULL,NULL,1,0,'F','0','0','distribution:withdrawal:query','#','admin','2026-10-12 23:01:16','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2285,'提现审核',2282,3,'',NULL,NULL,1,0,'F','0','0','distribution:withdrawal:audit','#','admin','2026-10-12 23:01:16','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2286,'确认打款',2282,4,'',NULL,NULL,1,0,'F','0','0','distribution:withdrawal:pay','#','admin','2026-10-12 23:01:16','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2287,'查看敏感资料',2282,5,'',NULL,NULL,1,0,'F','0','0','distribution:withdrawal:sensitive','#','admin','2026-10-12 23:01:16','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2288,'分销设置',2270,5,'config','distribution/config/index',NULL,1,0,'C','0','0','distribution:config:query','icon-settings','admin','2026-10-12 23:01:16','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2289,'分销设置查询',2288,1,'',NULL,NULL,1,0,'F','0','0','distribution:config:query','#','admin','2026-10-12 23:01:16','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2290,'分销设置修改',2288,2,'',NULL,NULL,1,0,'F','0','0','distribution:config:edit','#','admin','2026-10-12 23:01:16','',NULL,'','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2291,'AI 生成配置查询',2204,7,'',NULL,NULL,1,0,'F','0','0','system:generationconfig:list','#','admin','2026-10-12 23:01:16','',NULL,'仅查询 mobile.generation.* 固定配置','0');
INSERT INTO `sys_menu` (`menu_id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `query`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2292,'AI 生成配置编辑',2204,8,'',NULL,NULL,1,0,'F','0','0','system:generationconfig:edit','#','admin','2026-10-12 23:01:16','',NULL,'仅修改 mobile.generation.* 固定配置','0');

--
-- Table structure for table `sys_mobile_tabbar`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_mobile_tabbar` (
  `tab_id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `tab_key` varchar(32) NOT NULL COMMENT 'Tab 唯一标识(前端固定,只读)',
  `tab_name` varchar(50) DEFAULT NULL COMMENT 'Tab 名称',
  `icon_normal` varchar(500) DEFAULT NULL COMMENT '普通态图标URL',
  `icon_active` varchar(500) DEFAULT NULL COMMENT '选中态图标URL',
  `page_path` varchar(200) DEFAULT NULL COMMENT '页面路径(前端固定,只读)',
  `sort` int DEFAULT '0' COMMENT '排序号(升序)',
  `visible` tinyint DEFAULT '1' COMMENT '是否显示(1显示/0隐藏)',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `del_flag` char(1) DEFAULT '0' COMMENT '逻辑删除标记(0存在/2删除)',
  PRIMARY KEY (`tab_id`),
  UNIQUE KEY `uk_tab_key` (`tab_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='移动端底部导航配置表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_mobile_tabbar`
--

INSERT INTO `sys_mobile_tabbar` (`tab_id`, `tab_key`, `tab_name`, `icon_normal`, `icon_active`, `page_path`, `sort`, `visible`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (1,'chat','对话','','','/pages/index/index',0,1,'admin','2026-10-12 23:01:15','',NULL,NULL,'0');
INSERT INTO `sys_mobile_tabbar` (`tab_id`, `tab_key`, `tab_name`, `icon_normal`, `icon_active`, `page_path`, `sort`, `visible`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2,'create','生成','','','/pages/create/index',1,1,'admin','2026-10-12 23:01:15','',NULL,NULL,'0');
INSERT INTO `sys_mobile_tabbar` (`tab_id`, `tab_key`, `tab_name`, `icon_normal`, `icon_active`, `page_path`, `sort`, `visible`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (3,'cases','案例','','','/pages/cases/index',2,1,'admin','2026-10-12 23:01:15','',NULL,NULL,'0');
INSERT INTO `sys_mobile_tabbar` (`tab_id`, `tab_key`, `tab_name`, `icon_normal`, `icon_active`, `page_path`, `sort`, `visible`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (4,'profile','我的','','','/pages/user/index',3,1,'admin','2026-10-12 23:01:15','',NULL,NULL,'0');

--
-- Table structure for table `sys_oper_log`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_oper_log` (
  `oper_id` bigint NOT NULL AUTO_INCREMENT COMMENT '日志主键',
  `title` varchar(50) DEFAULT '' COMMENT '模块标题',
  `business_type` int DEFAULT '0' COMMENT '业务类型（0其它 1新增 2修改 3删除）',
  `method` varchar(100) DEFAULT '' COMMENT '方法名称',
  `request_method` varchar(10) DEFAULT '' COMMENT '请求方式',
  `oper_name` varchar(50) DEFAULT '' COMMENT '操作人员',
  `oper_url` varchar(255) DEFAULT '' COMMENT '请求URL',
  `oper_ip` varchar(128) DEFAULT '' COMMENT '主机地址',
  `oper_location` varchar(255) DEFAULT '' COMMENT '操作地点',
  `oper_param` varchar(2000) DEFAULT '' COMMENT '请求参数',
  `json_result` varchar(2000) DEFAULT '' COMMENT '返回参数',
  `status` int DEFAULT '0' COMMENT '操作状态（0正常 1异常）',
  `error_msg` varchar(2000) DEFAULT '' COMMENT '错误消息',
  `oper_time` datetime DEFAULT NULL COMMENT '操作时间',
  `cost_time` bigint DEFAULT '0' COMMENT '消耗时间（毫秒）',
  PRIMARY KEY (`oper_id`),
  KEY `idx_sys_oper_log_bt` (`business_type`),
  KEY `idx_sys_oper_log_s` (`status`),
  KEY `idx_sys_oper_log_ot` (`oper_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='操作日志记录';
/*!40101 SET character_set_client = @saved_cs_client */;


--
-- Table structure for table `sys_post`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_post` (
  `post_id` bigint NOT NULL AUTO_INCREMENT COMMENT '岗位ID',
  `post_code` varchar(64) NOT NULL COMMENT '岗位编码',
  `post_name` varchar(50) NOT NULL COMMENT '岗位名称',
  `post_sort` int DEFAULT '0' COMMENT '显示顺序',
  `status` char(1) DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='岗位信息表';
/*!40101 SET character_set_client = @saved_cs_client */;


--
-- Table structure for table `sys_role`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role` (
  `role_id` bigint NOT NULL AUTO_INCREMENT COMMENT '角色ID',
  `role_name` varchar(30) NOT NULL COMMENT '角色名称',
  `role_key` varchar(100) NOT NULL COMMENT '角色权限字符串',
  `role_sort` int DEFAULT '0' COMMENT '显示顺序',
  `data_scope` char(1) DEFAULT '1' COMMENT '数据范围（1：全部数据权限 2：自定数据权限 3：本部门数据权限 4：本部门及以下数据权限 5：仅本人数据权限）',
  `status` char(1) DEFAULT '0' COMMENT '角色状态（0正常 1停用）',
  `del_flag` char(1) DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `is_admin` tinyint DEFAULT '0' COMMENT 'æ˜¯å¦ä¸ºè¶…çº§ç®¡ç†å‘˜ï¼ˆ0å¦ 1æ˜¯ï¼‰',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_role`
--

INSERT INTO `sys_role` (`role_id`, `role_name`, `role_key`, `role_sort`, `data_scope`, `status`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`, `is_admin`, `remark`) VALUES (1,'超级管理员','admin',1,'1','0','0','','2025-12-18 13:21:48','',NULL,1,'超级管理员，拥有所有权限');
INSERT INTO `sys_role` (`role_id`, `role_name`, `role_key`, `role_sort`, `data_scope`, `status`, `del_flag`, `create_by`, `create_time`, `update_by`, `update_time`, `is_admin`, `remark`) VALUES (2,'普通用户','common',2,'5','0','0','','2025-12-18 13:21:48','',NULL,0,'普通用户，仅本人数据权限');

--
-- Table structure for table `sys_role_menu`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role_menu` (
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `menu_id` bigint NOT NULL COMMENT '菜单ID',
  PRIMARY KEY (`role_id`,`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色和菜单关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_role_menu`
--

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,1);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,100);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,101);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,102);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,103);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,104);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,105);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,106);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,200);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,201);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,202);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,203);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,204);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,205);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,300);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,301);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,302);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,303);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,304);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,2006);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,2007);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,2008);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,2009);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,2010);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,2011);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,2012);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,2013);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,2014);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,2015);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,2016);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,2017);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,2018);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,2019);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,2020);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,2021);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,2022);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,2023);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,2049);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,2050);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,2051);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,2052);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,2053);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,2054);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,2055);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,2101);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,2102);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,2103);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,2104);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,2105);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,2110);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,2111);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,2112);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,2113);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,2114);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,2117);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,2118);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,2119);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,2120);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,2121);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,2252);
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,2292);

--
-- Table structure for table `sys_template_category`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_template_category` (
  `category_id` bigint NOT NULL AUTO_INCREMENT COMMENT '行业分类ID',
  `name` varchar(32) NOT NULL COMMENT '行业分类名称',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序(小在前)',
  `status` char(1) NOT NULL DEFAULT '0' COMMENT '状态(0启用/1停用)',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `del_flag` char(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除(0存在/2删除)',
  PRIMARY KEY (`category_id`),
  KEY `idx_template_category_status_sort` (`status`,`sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='案例行业分类表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_template_category`
--

INSERT INTO `sys_template_category` (`category_id`, `name`, `sort`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (1,'美发',1,'0','system','2026-10-12 23:01:16','',NULL,NULL,'0');
INSERT INTO `sys_template_category` (`category_id`, `name`, `sort`, `status`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`, `del_flag`) VALUES (2,'美甲',2,'0','system','2026-10-12 23:01:16','',NULL,NULL,'0');

--
-- Table structure for table `sys_user`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user` (
  `user_id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `dept_id` bigint DEFAULT NULL COMMENT '部门ID',
  `user_name` varchar(30) NOT NULL COMMENT '用户账号',
  `nick_name` varchar(50) DEFAULT '',
  `user_type` varchar(2) DEFAULT '00' COMMENT '用户类型（00系统用户）',
  `email` varchar(50) DEFAULT '' COMMENT '用户邮箱',
  `phonenumber` varchar(11) DEFAULT '' COMMENT '手机号码',
  `sex` char(1) DEFAULT '0' COMMENT '用户性别（0男 1女 2未知）',
  `avatar` varchar(100) DEFAULT '' COMMENT '头像地址',
  `password` varchar(100) DEFAULT '' COMMENT '密码',
  `status` char(1) DEFAULT '0' COMMENT '帐号状态（0正常 1停用）',
  `del_flag` char(1) DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
  `login_ip` varchar(128) DEFAULT '' COMMENT '最后登录IP',
  `login_date` datetime DEFAULT NULL COMMENT '最后登录时间',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_user`
--

INSERT INTO `sys_user` (`user_id`, `dept_id`, `user_name`, `nick_name`, `user_type`, `email`, `phonenumber`, `sex`, `avatar`, `password`, `status`, `del_flag`, `login_ip`, `login_date`, `create_by`, `create_time`, `update_by`, `update_time`, `remark`) VALUES (1,100,'admin','系统管理员','00','','','0','','$2a$10$OwC7df.//VmPyxey/zfQ7ekSa.r5UNBlM8HX0ij6zDObI2LiRDrM.','0','0','',NULL,'','2025-12-24 19:08:37','',NULL,'管理员');

--
-- Table structure for table `sys_user_post`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user_post` (
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `post_id` bigint NOT NULL COMMENT '岗位ID',
  PRIMARY KEY (`user_id`,`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户与岗位关联表';
/*!40101 SET character_set_client = @saved_cs_client */;


--
-- Table structure for table `sys_user_role`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user_role` (
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  PRIMARY KEY (`user_id`,`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户和角色关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_user_role`
--

INSERT INTO `sys_user_role` (`user_id`, `role_id`) VALUES (1,1);

--
-- Table structure for table `sys_wx_config`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_wx_config` (
  `wx_id` bigint NOT NULL AUTO_INCREMENT COMMENT '配置ID',
  `app_type` varchar(20) NOT NULL COMMENT '应用类型(mini_app小程序/pay支付)',
  `app_name` varchar(100) DEFAULT NULL COMMENT '应用名称',
  `app_id` varchar(64) NOT NULL COMMENT '微信 AppId',
  `app_secret` varchar(255) DEFAULT NULL COMMENT '小程序密钥(加密存储)',
  `mch_id` varchar(32) DEFAULT NULL COMMENT '微信支付商户号',
  `api_v3_key` varchar(255) DEFAULT NULL COMMENT '支付 APIv3 密钥(加密存储)',
  `private_key` text COMMENT '证书私钥内容(加密存储)',
  `private_cert` text COMMENT '证书内容(加密存储)',
  `notify_url` varchar(255) DEFAULT NULL COMMENT '支付回调地址',
  `is_active` tinyint DEFAULT '0' COMMENT '是否启用(1启用/0停用)',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `public_key` text COMMENT '微信支付公钥pub_key.pem内容',
  `public_key_id` varchar(128) DEFAULT NULL COMMENT '微信支付公钥ID(PUB_KEY_ID_前缀)',
  `offer_id` varchar(64) DEFAULT NULL COMMENT '虚拟支付商户号 OfferId',
  `sandbox_app_key` varchar(512) DEFAULT NULL COMMENT '沙箱 AppKey(env=1,加密存储)',
  `prod_app_key` varchar(512) DEFAULT NULL COMMENT '现网 AppKey(env=0,加密存储)',
  `env` int DEFAULT NULL COMMENT '虚拟支付环境:0现网/1沙箱',
  `push_token` varchar(512) DEFAULT NULL COMMENT '消息推送 Token(验签,加密存储)',
  `push_aes_key` varchar(512) DEFAULT NULL COMMENT '消息推送 EncodingAESKey(解密,加密存储)',
  PRIMARY KEY (`wx_id`),
  UNIQUE KEY `uk_appid_type` (`app_id`,`app_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='微信应用配置表';
/*!40101 SET character_set_client = @saved_cs_client */;


--
-- Table structure for table `template`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `template` (
  `template_id` bigint NOT NULL AUTO_INCREMENT COMMENT '模板ID',
  `category` varchar(8) NOT NULL COMMENT '分类(image图片/video视频)',
  `industry_category_id` bigint NOT NULL COMMENT '行业分类ID',
  `title` varchar(64) NOT NULL COMMENT '案例标题',
  `cover_url` varchar(512) DEFAULT NULL COMMENT '封面图 URL',
  `prompt` text NOT NULL COMMENT '提示词文本(必填)',
  `params` text COMMENT '参数预设(存 JSON 字符串,结构按分类)',
  `result_media` text COMMENT '效果媒体 URL 列表(存 JSON 数组字符串)',
  `reference_images` text COMMENT 'JSON 数组:图生图模板预置参考图 /profile URL(1~4)',
  `tags` varchar(128) DEFAULT NULL COMMENT '标签(逗号分隔)',
  `use_count` bigint NOT NULL DEFAULT '0' COMMENT '使用次数(本期仅展示)',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序(小在前)',
  `is_featured` tinyint(1) DEFAULT '0' COMMENT '本周精选(1是/0否,后台选最多5个)',
  `status` char(1) NOT NULL DEFAULT '0' COMMENT '上下架(0上架/1下架)',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `del_flag` char(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除标记(0存在/2删除)',
  PRIMARY KEY (`template_id`),
  KEY `idx_template_media_industry_status` (`category`,`industry_category_id`,`status`,`del_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='移动端案例/模板表';
/*!40101 SET character_set_client = @saved_cs_client */;


--
-- Table structure for table `user_membership`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_membership` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `level_id` bigint DEFAULT NULL COMMENT '引用的等级ID(可空,配置被删后仍保留)',
  `level_sort` int NOT NULL DEFAULT '0' COMMENT '快照:等级序号(取最高用)',
  `level_name` varchar(64) NOT NULL COMMENT '快照:等级名称',
  `snapshot_grant_beans` bigint NOT NULL DEFAULT '0' COMMENT '快照:每周期送豆数',
  `snapshot_price` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '快照:价格(元)',
  `source` varchar(16) NOT NULL COMMENT '来源(MANUAL手动/PAY支付)',
  `source_order_no` varchar(32) DEFAULT NULL COMMENT '支付来源商户订单号(支付会员幂等)',
  `paid_time` datetime DEFAULT NULL COMMENT 'PAY会员段真实支付时间(创建后不可变)',
  `start_time` datetime NOT NULL COMMENT '生效时间',
  `expire_time` datetime NOT NULL COMMENT '到期时间',
  `expiry_settled_time` datetime DEFAULT NULL COMMENT '作为最后有效段时完成豆子清零结算的时间',
  `status` char(1) NOT NULL DEFAULT '0' COMMENT '状态(0有效/1过期/2取消)',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_membership_source_order` (`source_order_no`),
  KEY `idx_user_status` (`user_id`,`status`),
  KEY `idx_status_expire_user` (`status`,`expire_time`,`user_id`),
  KEY `idx_status_settled_expire_user` (`status`,`expiry_settled_time`,`expire_time`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='移动端用户会员段表(叠加制)';
/*!40101 SET character_set_client = @saved_cs_client */;


--
-- Table structure for table `user_prompt`
--

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_prompt` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT 'mobile_user.id(唯一,每用户一条)',
  `content` text COMMENT '自由文本(写入前 HtmlUtils.htmlUnescape)',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `del_flag` char(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除标记(0存在/2删除)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 闭环用户个性化提示词';
/*!40101 SET character_set_client = @saved_cs_client */;

/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;
