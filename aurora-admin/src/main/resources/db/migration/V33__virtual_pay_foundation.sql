-- 迁移:小程序虚拟支付 P1 基础字段
-- 说明:
--   1. 保存 code2Session 返回的 session_key(AES 密文),供 requestVirtualPayment 用户态签名。
--   2. 会员等级/豆子套餐关联微信公众平台普通道具 ID;豆子套餐补商品名称。
--   3. 支付订单记录支付渠道、微信虚拟订单号、环境、商品/权益快照与履约状态。
--   4. 支付会员段记录来源订单号,由唯一索引提供最终幂等保护。
-- 存量兼容:
--   - 除 pay_channel 外新增业务字段均允许 NULL;存量订单 pay_channel 由默认值回填为 WXPAY。
--   - virtual_product_id / wx_order_id / source_order_no 的唯一索引允许多行 NULL。
--   - 不对 bean_transaction 历史数据直接添加唯一索引,避免未知存量重复值阻断 Flyway。

ALTER TABLE `mobile_user`
    ADD COLUMN `wx_session_key` VARCHAR(512) NULL COMMENT '微信 session_key(AES 加密存储)' AFTER `wx_unionid`,
    ADD COLUMN `session_key_updated` DATETIME NULL COMMENT '微信 session_key 最近刷新时间' AFTER `wx_session_key`;

ALTER TABLE `membership_level`
    ADD COLUMN `virtual_product_id` VARCHAR(20) NULL COMMENT '微信虚拟支付普通道具ID' AFTER `duration_days`,
    ADD UNIQUE KEY `uk_membership_virtual_product` (`virtual_product_id`);

ALTER TABLE `bean_recharge_package`
    ADD COLUMN `name` VARCHAR(64) NULL COMMENT '套餐名称' AFTER `package_id`,
    ADD COLUMN `virtual_product_id` VARCHAR(20) NULL COMMENT '微信虚拟支付普通道具ID' AFTER `name`,
    ADD UNIQUE KEY `uk_bean_package_virtual_product` (`virtual_product_id`);

-- 豆子套餐补一个可编辑的兜底名称；不在 schema 迁移里改商品上下架状态。
-- 虚拟支付启用时，业务查询会动态隐藏未映射道具的存量商品，应用回滚不会丢失原状态。
UPDATE `bean_recharge_package`
SET `name` = COALESCE(NULLIF(`name`, ''), CONCAT('豆子套餐-', `beans`, '豆'));

ALTER TABLE `pay_order`
    ADD COLUMN `pay_channel` VARCHAR(16) NOT NULL DEFAULT 'WXPAY' COMMENT '支付渠道(WXPAY/VIRTUAL)' AFTER `product_name`,
    ADD COLUMN `wx_order_id` VARCHAR(64) NULL COMMENT '微信虚拟支付订单号' AFTER `pay_channel`,
    ADD COLUMN `env` TINYINT NULL COMMENT '虚拟支付环境(0现网/1沙箱)' AFTER `wx_order_id`,
    ADD COLUMN `virtual_product_id` VARCHAR(20) NULL COMMENT '微信普通道具ID快照' AFTER `env`,
    ADD COLUMN `snapshot_beans` BIGINT NULL COMMENT '豆子套餐到账豆数快照' AFTER `virtual_product_id`,
    ADD COLUMN `snapshot_duration_days` INT NULL COMMENT '会员有效天数快照' AFTER `snapshot_beans`,
    ADD COLUMN `snapshot_grant_beans` BIGINT NULL COMMENT '会员赠豆数快照' AFTER `snapshot_duration_days`,
    ADD COLUMN `snapshot_level_sort` INT NULL COMMENT '会员等级排序快照' AFTER `snapshot_grant_beans`,
    ADD COLUMN `fulfillment_status` TINYINT NULL COMMENT '履约状态(0待发放/1已发放/2失败)' AFTER `snapshot_level_sort`,
    ADD COLUMN `fulfillment_error` VARCHAR(500) NULL COMMENT '最近履约失败原因' AFTER `fulfillment_status`,
    ADD COLUMN `fulfilled_at` DATETIME NULL COMMENT '履约完成时间' AFTER `fulfillment_error`,
    ADD COLUMN `delivery_confirmed` TINYINT NOT NULL DEFAULT 0 COMMENT '微信侧发货确认(0待确认/1已确认)' AFTER `fulfilled_at`,
    ADD COLUMN `delivery_confirmed_at` DATETIME NULL COMMENT '微信侧发货确认时间' AFTER `delivery_confirmed`,
    ADD UNIQUE KEY `uk_pay_order_wx_order_id` (`wx_order_id`),
    ADD KEY `idx_pay_order_channel_status` (`pay_channel`, `status`, `create_time`),
    ADD KEY `idx_pay_order_fulfillment` (`fulfillment_status`, `create_time`),
    ADD KEY `idx_pay_order_delivery_confirmed` (`delivery_confirmed`, `create_time`),
    ADD KEY `idx_pay_order_virtual_product` (`virtual_product_id`);

ALTER TABLE `user_membership`
    ADD COLUMN `source_order_no` VARCHAR(32) NULL COMMENT '支付来源商户订单号(支付会员幂等)' AFTER `source`,
    ADD UNIQUE KEY `uk_user_membership_source_order` (`source_order_no`);
