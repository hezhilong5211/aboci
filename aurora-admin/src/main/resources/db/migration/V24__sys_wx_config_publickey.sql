-- 迁移:微信支付配置 · 公钥字段(适配微信支付公钥模式)
-- 日期:2026-06-30
-- 说明:
--   sys_wx_config 新增两列,用于微信支付平台公钥验签(替代/兼容平台证书模式):
--     public_key   :微信支付公钥 pub_key.pem 内容(敏感配置,加解密/脱敏由后台 Controller 处理)。
--     public_key_id:微信支付公钥ID(PUB_KEY_ID_ 前缀)。
-- Flyway:本目录受 Flyway 管控,启动时按版本顺序自动执行,仅写增量。

ALTER TABLE `sys_wx_config`
    ADD COLUMN `public_key`    TEXT         NULL COMMENT '微信支付公钥pub_key.pem内容',
    ADD COLUMN `public_key_id` VARCHAR(128) NULL COMMENT '微信支付公钥ID(PUB_KEY_ID_前缀)';
