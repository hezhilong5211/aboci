package com.aurora.admin.mobile.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 微信小程序 requestVirtualPayment 失败回调诊断数据。
 * 仅写入服务日志，不作为订单状态或支付结果的判定依据。
 */
@Data
public class VirtualPayClientFailureDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 服务端创建的商户订单号。 */
    private String orderNo;

    /** wx.requestVirtualPayment fail 回调的 errCode。 */
    private String errCode;

    /** wx.requestVirtualPayment fail 回调的 errMsg。 */
    private String errMsg;

    /** fail 回调对象的 JSON 摘要，保留微信返回的其他诊断字段。 */
    private String rawError;

    /** 客户端上下文，仅用于排障。 */
    private String productType;
    private Long productId;
    private String platform;
    private String system;
    private String model;
    private String wechatVersion;
    private String sdkVersion;
    private String miniProgramVersion;
    private String envVersion;
}
