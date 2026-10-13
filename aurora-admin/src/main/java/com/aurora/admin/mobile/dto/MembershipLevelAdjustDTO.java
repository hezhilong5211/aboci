package com.aurora.admin.mobile.dto;

import lombok.Data;

/**
 * 后台调整移动端用户当前会员等级。
 */
@Data
public class MembershipLevelAdjustDTO {

    /** 目标等级ID；null 表示调整为无会员。 */
    private Long levelId;
}
