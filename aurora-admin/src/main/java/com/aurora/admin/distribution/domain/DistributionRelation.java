package com.aurora.admin.distribution.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("distribution_relation")
public class DistributionRelation {
    @TableId(value = "relation_id", type = IdType.AUTO)
    private Long relationId;
    private Long partnerId;
    private Long referredUserId;
    private String inviteCode;
    private LocalDateTime firstVisitTime;
    private LocalDateTime bindTime;
    private Integer existingUser;
    private String status;
    private LocalDateTime createTime;
}
