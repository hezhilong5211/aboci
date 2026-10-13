-- Agent 澄清状态：精确范围绑定、问题去重与过期保护。

ALTER TABLE `reference_scope`
    ADD COLUMN `context_json` MEDIUMTEXT DEFAULT NULL COMMENT '已问问题、答案和选项的紧凑上下文' AFTER `allow_additional_images`,
    ADD COLUMN `round_no` INT NOT NULL DEFAULT 1 COMMENT '澄清轮数，从1开始' AFTER `context_json`,
    ADD COLUMN `expires_at` DATETIME DEFAULT NULL COMMENT '澄清范围过期时间' AFTER `round_no`;

CREATE INDEX `idx_scope_expire`
    ON `reference_scope` (`status`, `expires_at`);
