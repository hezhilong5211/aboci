-- 会员有效期改为按等级独立配置。只解除固定 365 天约束，不修改历史会员段和订单快照。

ALTER TABLE `membership_level`
    DROP CHECK `chk_membership_duration_365`;

ALTER TABLE `membership_level`
    MODIFY COLUMN `duration_days` INT NOT NULL DEFAULT 365 COMMENT '会员时长(天)',
    ADD CONSTRAINT `chk_membership_duration_range`
        CHECK (`duration_days` BETWEEN 1 AND 3650);
