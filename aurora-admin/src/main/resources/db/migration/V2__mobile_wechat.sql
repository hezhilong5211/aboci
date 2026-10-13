-- 迁移：移动端微信登录/支付地基三表
-- 日期：2026-06-21
-- 说明：新建 mobile_user / pay_order / sys_wx_config 三张表,支撑小程序登录、微信支付与多微信应用配置管理。
--      字符集统一 utf8mb4_0900_ai_ci;审计字段 create_time/update_time;mobile_user 带逻辑删 del_flag(0存在/2删除)。
--      唯一约束:mobile_user.uk_openid(wx_openid)、pay_order.uk_order_no(order_no)、sys_wx_config.uk_appid_type(app_id,app_type)。
-- Flyway:本目录受 Flyway 管控,启动时按版本顺序自动执行(基线为 V1,本脚本为 V2 增量)。仅写增量,不动存量数据。

-- ===== 移动端用户表 =====
CREATE TABLE IF NOT EXISTS `mobile_user` (
    `user_id`     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `wx_openid`   VARCHAR(64)  DEFAULT NULL COMMENT '微信小程序 openid',
    `wx_unionid`  VARCHAR(64)  DEFAULT NULL COMMENT '微信 unionid(同主体多端唯一)',
    `phone`       VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
    `password`    VARCHAR(100) DEFAULT NULL COMMENT '密码(加密存储)',
    `nick_name`   VARCHAR(50)  DEFAULT NULL COMMENT '昵称',
    `avatar_url`  VARCHAR(500) DEFAULT NULL COMMENT '头像URL',
    `gender`      TINYINT      DEFAULT NULL COMMENT '性别(0未知/1男/2女)',
    `source`      VARCHAR(20)  DEFAULT NULL COMMENT '来源渠道',
    `status`      VARCHAR(1)   DEFAULT '0' COMMENT '状态(0正常/1停用)',
    `del_flag`    CHAR(1)      DEFAULT '0' COMMENT '逻辑删除标记(0存在/2删除)',
    `create_time` DATETIME     DEFAULT NULL COMMENT '创建时间',
    `update_time` DATETIME     DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`user_id`),
    UNIQUE KEY `uk_openid` (`wx_openid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='移动端用户表';

-- ===== 支付订单表 =====
CREATE TABLE IF NOT EXISTS `pay_order` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `order_no`       VARCHAR(64)  NOT NULL COMMENT '商户订单号',
    `user_id`        BIGINT       DEFAULT NULL COMMENT '下单用户ID',
    `open_id`        VARCHAR(64)  DEFAULT NULL COMMENT '支付用户 openid',
    `amount`         INT          NOT NULL COMMENT '金额(单位:分)',
    `subject`        VARCHAR(255) DEFAULT NULL COMMENT '商品描述',
    `status`         TINYINT      NOT NULL DEFAULT 0 COMMENT '状态(0待支付/1已支付/2已退款/3已关闭)',
    `transaction_id` VARCHAR(64)  DEFAULT NULL COMMENT '微信支付交易单号',
    `pay_time`       DATETIME     DEFAULT NULL COMMENT '支付完成时间',
    `refund_no`      VARCHAR(64)  DEFAULT NULL COMMENT '退款单号',
    `refund_amount`  INT          DEFAULT NULL COMMENT '退款金额(单位:分)',
    `refund_time`    DATETIME     DEFAULT NULL COMMENT '退款时间',
    `create_time`    DATETIME     DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='支付订单表';

-- ===== 微信应用配置表 =====
CREATE TABLE IF NOT EXISTS `sys_wx_config` (
    `wx_id`        BIGINT        NOT NULL AUTO_INCREMENT COMMENT '配置ID',
    `app_type`     VARCHAR(20)   NOT NULL COMMENT '应用类型(mini_app小程序/pay支付)',
    `app_name`     VARCHAR(100)  DEFAULT NULL COMMENT '应用名称',
    `app_id`       VARCHAR(64)   NOT NULL COMMENT '微信 AppId',
    `app_secret`   VARCHAR(255)  DEFAULT NULL COMMENT '小程序密钥(加密存储)',
    `mch_id`       VARCHAR(32)   DEFAULT NULL COMMENT '微信支付商户号',
    `api_v3_key`   VARCHAR(255)  DEFAULT NULL COMMENT '支付 APIv3 密钥(加密存储)',
    `private_key`  TEXT          DEFAULT NULL COMMENT '证书私钥内容(加密存储)',
    `private_cert` TEXT          DEFAULT NULL COMMENT '证书内容(加密存储)',
    `notify_url`   VARCHAR(255)  DEFAULT NULL COMMENT '支付回调地址',
    `is_active`    TINYINT       DEFAULT 0 COMMENT '是否启用(1启用/0停用)',
    `create_time`  DATETIME      DEFAULT NULL COMMENT '创建时间',
    `update_time`  DATETIME      DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`wx_id`),
    UNIQUE KEY `uk_appid_type` (`app_id`, `app_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='微信应用配置表';
