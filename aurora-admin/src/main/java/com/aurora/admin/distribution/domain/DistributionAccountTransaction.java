package com.aurora.admin.distribution.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("distribution_account_transaction")
public class DistributionAccountTransaction {
    @TableId(value = "transaction_id", type = IdType.AUTO)
    private Long transactionId;
    private Long partnerId;
    private String type;
    private Long changeAmount;
    private Long availableAfter;
    private Long lockedAfter;
    private Long debtAfter;
    private String bizNo;
    private String sourceOrderNo;
    private String withdrawalNo;
    private String remark;
    private LocalDateTime createTime;
}
