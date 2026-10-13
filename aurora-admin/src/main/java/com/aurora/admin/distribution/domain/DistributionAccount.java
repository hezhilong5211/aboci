package com.aurora.admin.distribution.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("distribution_account")
public class DistributionAccount {
    @TableId(value = "partner_id", type = IdType.INPUT)
    private Long partnerId;
    private Long availableAmount;
    private Long withdrawLockedAmount;
    private Long totalWithdrawn;
    private Long totalReversed;
    private Long debtAmount;
    private Integer version;
    private LocalDateTime updateTime;
}
