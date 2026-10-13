-- 会员固定 30 天 + 到期豆子清零
-- 1. 所有会员商品时长统一为 30 天，并由数据库约束兜底。
-- 2. user_membership 增加到期扫描索引。
-- 3. bean_account 单独累计过期清零数量，避免把到期豆误记为真实消费。

UPDATE `membership_level`
SET `duration_days` = 30
WHERE `duration_days` <> 30;

ALTER TABLE `membership_level`
    MODIFY COLUMN `duration_days` INT NOT NULL DEFAULT 30 COMMENT '会员时长(固定30天)',
    ADD CONSTRAINT `chk_membership_duration_30` CHECK (`duration_days` = 30);

ALTER TABLE `user_membership`
    ADD COLUMN `paid_time` DATETIME NULL COMMENT 'PAY会员段真实支付时间(创建后不可变)' AFTER `source_order_no`,
    ADD COLUMN `expiry_settled_time` DATETIME NULL COMMENT '作为最后有效段时完成豆子清零结算的时间' AFTER `expire_time`,
    ADD KEY `idx_status_expire_user` (`status`, `expire_time`, `user_id`),
    ADD KEY `idx_status_settled_expire_user` (`status`, `expiry_settled_time`, `expire_time`, `user_id`);

-- 尽可能为 V34 前的 PAY 会员段固化真实支付时间；无法匹配的历史段保留 NULL，
-- 业务层会保守地保持原排期，绝不猜测支付时间并前移权益。
UPDATE `user_membership` um
JOIN `pay_order` po ON po.`order_no` = um.`source_order_no`
SET um.`paid_time` = po.`pay_time`
WHERE um.`source` = 'PAY'
  AND um.`paid_time` IS NULL
  AND po.`pay_time` IS NOT NULL;

ALTER TABLE `bean_account`
    ADD COLUMN `total_expired` BIGINT NOT NULL DEFAULT 0 COMMENT '累计因会员到期清零的豆子' AFTER `total_consumed`,
    ADD COLUMN `total_revoked` BIGINT NOT NULL DEFAULT 0 COMMENT '累计因支付订单退款回收的豆子' AFTER `total_expired`;

ALTER TABLE `bean_transaction`
    ADD COLUMN `source_biz_no` VARCHAR(128) NULL COMMENT '来源业务单号(续费恢复时为旧EXPIRE单号)' AFTER `biz_no`,
    MODIFY COLUMN `type` VARCHAR(16) NOT NULL
        COMMENT '类型(GIFT/ISSUE/RECHARGE/DEDUCT/REFUND/ADJUST/EXPIRE/REVOKE/RENEW_RESTORE)',
    ADD KEY `idx_user_type_biz` (`user_id`, `type`, `biz_no`),
    ADD UNIQUE KEY `uk_type_source_biz` (`type`, `source_biz_no`);

ALTER TABLE `pay_order`
    MODIFY COLUMN `status` TINYINT NOT NULL DEFAULT 0
        COMMENT '状态(0待支付/1已支付/2已退款/3已关闭/4已支付待退款)';
