package com.aurora.admin.distribution.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("distribution_visit")
public class DistributionVisit {
    @TableId(value = "visit_id", type = IdType.AUTO)
    private Long visitId;
    private String eventId;
    private Long partnerId;
    private Long userId;
    private String launchScene;
    private Integer bound;
    private LocalDateTime visitTime;
    private LocalDateTime bindTime;
}
