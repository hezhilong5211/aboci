package com.aurora.admin.distribution.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("distribution_withdrawal_allocation")
public class DistributionWithdrawalAllocation {
    @TableId(value = "allocation_id", type = IdType.AUTO)
    private Long allocationId;
    private Long withdrawalId;
    private Long commissionId;
    private Long allocatedAmount;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
