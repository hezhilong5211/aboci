-- 会员固定 365 天；存量未到期会员段一并延长并重新排成连续链。

ALTER TABLE `membership_level`
    DROP CHECK `chk_membership_duration_30`;

UPDATE `membership_level`
SET `duration_days` = 365
WHERE `duration_days` <> 365;

ALTER TABLE `membership_level`
    MODIFY COLUMN `duration_days` INT NOT NULL DEFAULT 365 COMMENT '会员时长(固定365天)',
    ADD CONSTRAINT `chk_membership_duration_365` CHECK (`duration_days` = 365);

-- 同一用户可能已购买多个首尾相接的30天会员段。先固化顺序，再从最早未到期段起
-- 按每段365天重新排期，避免简单延长 expire_time 造成段之间重叠。
CREATE TEMPORARY TABLE `tmp_membership_365_schedule` AS
SELECT `id`,
       DATE_ADD(`chain_start`, INTERVAL (`segment_no` - 1) * 365 DAY) AS `new_start_time`,
       DATE_ADD(`chain_start`, INTERVAL `segment_no` * 365 DAY) AS `new_expire_time`
FROM (
    SELECT `id`,
           FIRST_VALUE(`start_time`) OVER (
               PARTITION BY `user_id` ORDER BY `start_time`, `id`
           ) AS `chain_start`,
           ROW_NUMBER() OVER (
               PARTITION BY `user_id` ORDER BY `start_time`, `id`
           ) AS `segment_no`
    FROM `user_membership`
    WHERE `status` = '0' AND `expire_time` > NOW()
) `ranked_membership`;

UPDATE `user_membership` `um`
JOIN `tmp_membership_365_schedule` `schedule` ON `schedule`.`id` = `um`.`id`
SET `um`.`start_time` = `schedule`.`new_start_time`,
    `um`.`expire_time` = `schedule`.`new_expire_time`,
    `um`.`expiry_settled_time` = NULL,
    `um`.`update_time` = NOW();

DROP TEMPORARY TABLE `tmp_membership_365_schedule`;

UPDATE `pay_order` `po`
JOIN `user_membership` `um` ON `um`.`source_order_no` = `po`.`order_no`
SET `po`.`snapshot_duration_days` = 365
WHERE `po`.`product_type` = 'MEMBERSHIP'
  AND `um`.`status` = '0'
  AND `um`.`expire_time` > NOW();
