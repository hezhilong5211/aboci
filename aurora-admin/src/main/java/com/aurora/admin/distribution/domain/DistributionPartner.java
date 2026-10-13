package com.aurora.admin.distribution.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("distribution_partner")
public class DistributionPartner {
    @TableId(value = "partner_id", type = IdType.AUTO)
    private Long partnerId;
    private Long userId;
    private String partnerNo;
    private String inviteCode;
    private String status;
    private LocalDateTime enabledTime;
    private LocalDateTime disabledTime;
    private String remark;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
    @TableLogic
    private String delFlag;
}
