package com.aurora.admin.mobile.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单查询出参
 */
@Data
public class OrderVO {

    /** 商户订单号 */
    private String orderNo;

    /** 状态(0待支付/1已支付/2已退款/3已关闭/4已支付待退款) */
    private Integer status;

    /** 状态文案 */
    private String statusText;

    /** 金额(单位:分) */
    private Integer amount;

    /** 商品描述 */
    private String subject;

    /** 支付完成时间 */
    private LocalDateTime payTime;

    /** 履约状态(0待发放/1已发放/2失败) */
    private Integer fulfillmentStatus;

    /** 履约失败或待退款原因 */
    private String fulfillmentError;
}
