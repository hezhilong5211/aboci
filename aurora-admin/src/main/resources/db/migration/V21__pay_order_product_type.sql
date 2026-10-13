-- 迁移:支付订单 · 商品类型字段(支持会员购买 + 豆子充值两类下单)
-- 日期:2026-06-30
-- 说明:
--   pay_order 新增三列,用于区分订单业务类型并记录商品快照,便于回调入账时按类型分派、对账时溯源:
--     product_type:订单类型(MEMBERSHIP 会员 / BEAN_RECHARGE 豆子充值),可空兼容存量历史订单。
--     product_id  :关联业务id(会员等级id / 豆子套餐id),可空。
--     product_name:商品名称快照(下单时固化,避免后续配置变更影响历史订单展示)。
-- Flyway:本目录受 Flyway 管控,启动时按版本顺序自动执行,仅写增量。

ALTER TABLE `pay_order`
    ADD COLUMN `product_type` VARCHAR(16)  NULL COMMENT '订单类型MEMBERSHIP/BEAN_RECHARGE',
    ADD COLUMN `product_id`   BIGINT       NULL COMMENT '关联业务id(会员等级id/套餐id)',
    ADD COLUMN `product_name` VARCHAR(255) NULL COMMENT '商品名称快照';
