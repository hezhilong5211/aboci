package com.aurora.admin.mobile.dto;

import lombok.Data;

/**
 * 会员手动开通请求(叠加制:每次新建一段会员)
 */
@Data
public class GrantMembershipDTO {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 等级ID
     */
    private Long levelId;

    /** 兼容旧管理端保留；服务端始终以所选等级当前配置为准。 */
    private Integer durationDays;
}
