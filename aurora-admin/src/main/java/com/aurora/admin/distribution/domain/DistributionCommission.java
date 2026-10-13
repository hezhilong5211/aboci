package com.aurora.admin.distribution.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("distribution_commission")
public class DistributionCommission {
    @TableId(value = "commission_id", type = IdType.AUTO)
    private Long commissionId;
    private Long partnerId;
    private Long referredUserId;
    private String orderNo;
    private String productType;
    private String productName;
    private Long orderAmount;
    private Integer rateBps;
    private Long commissionAmount;
    private Long availableAmount;
    private Long lockedAmount;
    private Long withdrawnAmount;
    private Long reversedAmount;
    private Long debtOffsetAmount;
    private String status;
    private LocalDateTime payTime;
    private String refundNo;
    private LocalDateTime refundTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
