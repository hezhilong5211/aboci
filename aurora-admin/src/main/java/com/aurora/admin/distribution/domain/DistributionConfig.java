package com.aurora.admin.distribution.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("distribution_config")
public class DistributionConfig {
    @TableId(value = "config_id", type = IdType.INPUT)
    private Long configId;
    private Integer enabled;
    private Integer commissionRateBps;
    private Long minWithdrawAmount;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
}
