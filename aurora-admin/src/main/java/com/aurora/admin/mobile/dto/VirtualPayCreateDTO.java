package com.aurora.admin.mobile.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 虚拟支付下单入参。
 * 客户端只允许提交本地商品类型和商品 ID，价格、微信道具 ID 与权益均由服务端读取。
 */
@Data
public class VirtualPayCreateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 商品类型：MEMBERSHIP / BEAN_RECHARGE */
    private String productType;

    /** 本地会员等级 ID 或豆子套餐 ID */
    private Long productId;

    /** 客户端平台，仅用于 iOS 沙箱前置阻断（不参与支付安全判定） */
    private String clientPlatform;

    /** session 过期重试时被替换的旧订单号，服务端仅关闭本人待支付虚拟订单 */
    private String replacesOrderNo;
}
