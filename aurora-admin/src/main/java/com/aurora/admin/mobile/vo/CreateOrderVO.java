package com.aurora.admin.mobile.vo;

import lombok.Data;

/**
 * 创建支付订单出参
 * orderNo 为商户订单号,payParams 为前端调起支付所需参数。
 */
@Data
public class CreateOrderVO {

    /** 商户订单号 */
    private String orderNo;

    /** 微信支付调起参数 */
    private PayParamsVO payParams;
}
