-- 虚拟支付配置:承载于 sys_wx_config(appType='virtual_pay' 行),复用加解密/脱敏/启用开关。
-- 新增列仅 virtual_pay 行使用,其余行(mini_app/pay)保持 NULL。
-- 敏感项(sandbox_app_key/prod_app_key/push_token/push_aes_key)AES 加密入库、出参脱敏。

ALTER TABLE `sys_wx_config`
    ADD COLUMN `offer_id` VARCHAR(64) NULL COMMENT '虚拟支付商户号 OfferId' AFTER `public_key_id`,
    ADD COLUMN `sandbox_app_key` VARCHAR(512) NULL COMMENT '沙箱 AppKey(env=1,加密存储)' AFTER `offer_id`,
    ADD COLUMN `prod_app_key` VARCHAR(512) NULL COMMENT '现网 AppKey(env=0,加密存储)' AFTER `sandbox_app_key`,
    ADD COLUMN `env` INT NULL COMMENT '虚拟支付环境:0现网/1沙箱' AFTER `prod_app_key`,
    ADD COLUMN `push_token` VARCHAR(512) NULL COMMENT '消息推送 Token(验签,加密存储)' AFTER `env`,
    ADD COLUMN `push_aes_key` VARCHAR(512) NULL COMMENT '消息推送 EncodingAESKey(解密,加密存储)' AFTER `push_token`;
