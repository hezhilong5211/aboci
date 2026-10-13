package com.aurora.admin.distribution.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;

@Data
@TableName("distribution_withdrawal")
public class DistributionWithdrawal {
    @TableId(value = "withdrawal_id", type = IdType.AUTO)
    private Long withdrawalId;
    private String withdrawalNo;
    private Long partnerId;
    private Long applyAmount;
    private String methodType;
    private String payeeName;
    private String bankName;
    @ToString.Exclude
    private String bankAccountCipher;
    private String bankAccountMasked;
    @ToString.Exclude
    private String receiptQrFileKey;
    private String status;
    private String rejectReason;
    private String paymentReference;
    @ToString.Exclude
    private String paymentProofFileKey;
    private LocalDateTime applyTime;
    private LocalDateTime auditTime;
    private LocalDateTime paidTime;
    private String auditBy;
    private String payBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
