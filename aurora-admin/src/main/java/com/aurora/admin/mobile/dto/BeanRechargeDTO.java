package com.aurora.admin.mobile.dto;

import lombok.Data;

/**
 * 豆子充值下单入参
 * 仅传套餐ID;到账豆数与金额一律服务端按套餐计算,前端不传金额。
 */
@Data
public class BeanRechargeDTO {

    /** 套餐ID */
    private Long packageId;
}
