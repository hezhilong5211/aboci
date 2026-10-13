package com.aurora.admin.mobile.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * C 端当前会员信息出参。
 * 非会员(无有效段)时:levelName=null、expireTime=null、remainDays=0、cardBg=null。
 * 有有效段时:等级名取当前段(有效段中 level_sort 最高一条)，到期时间取连续已购续费链末端;
 *   cardBg 按当前段 level_id 实时查 membership_level(等级已删则 null)。
 */
@Data
public class MembershipInfoVO {

    /** 当前等级名(非会员为 null) */
    private String levelName;

    /** 当前连续会员权益最终到期时间(含已购续期；非会员为 null) */
    private LocalDateTime expireTime;

    /** 剩余天数(向上取整,过期/非会员为 0) */
    private Integer remainDays;

    /** 会员卡片背景图URL(按当前段 level_id 查 membership_level.card_bg,等级已删为 null) */
    private String cardBg;
}
