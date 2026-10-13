package com.aurora.admin.mobile.dto;

import lombok.Data;

/**
 * 创建支付订单入参
 * openid 由服务端从用户表取,前端不传;amount 单位为分。
 */
@Data
public class CreateOrderDTO {

    /** 支付金额(单位:分) */
    private Integer amount;

    /** 商品描述 */
    private String subject;
}
