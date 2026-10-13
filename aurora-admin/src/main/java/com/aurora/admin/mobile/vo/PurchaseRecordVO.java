package com.aurora.admin.mobile.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 购买记录出参(C 端「设置-购买记录」只读列表,无操作字段)。
 * 不含 openId / transactionId 等敏感与内部字段。
 */
@Data
public class PurchaseRecordVO {

    /** 商户订单号(用户对账/客服凭据) */
    private String orderNo;

    /** 订单类型(MEMBERSHIP会员/BEAN_RECHARGE豆子充值;历史订单可空) */
    private String productType;

    /** 商品快照名(下单时固化) */
    private String productName;

    /** 商品描述(productName 为空时兜底展示) */
    private String subject;

    /** 金额(单位:分) */
    private Integer amount;

    /** 状态(0待支付/1已支付/2已退款/3已关闭/4已支付待退款) */
    private Integer status;

    /** 状态文案 */
    private String statusText;

    /** 支付完成时间 */
    private LocalDateTime payTime;

    /** 履约状态(0待发放/1已发放/2失败) */
    private Integer fulfillmentStatus;

    /** 履约失败或待退款原因 */
    private String fulfillmentError;

    /** 下单时间 */
    private LocalDateTime createTime;
}
