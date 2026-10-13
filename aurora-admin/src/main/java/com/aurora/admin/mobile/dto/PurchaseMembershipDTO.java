package com.aurora.admin.mobile.dto;

import lombok.Data;

/**
 * C 端会员购买入参
 * 仅传 levelId;价格与时长服务端按等级查取,绝不信任前端。
 */
@Data
public class PurchaseMembershipDTO {

    /** 会员等级ID */
    private Long levelId;
}
