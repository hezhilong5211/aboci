package com.aurora.admin.mobile.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 微信支付调起参数(给前端 wx.requestPayment 使用)
 * V3 的 signType 固定 "RSA";packageValue 形如 prepay_id=xxx,序列化键名为 package。
 */
@Data
public class PayParamsVO {

    /** 小程序 appId */
    private String appId;

    /** 时间戳(秒级 10 位字符串) */
    private String timeStamp;

    /** 随机字符串 */
    private String nonceStr;

    /** 订单详情扩展字符串,形如 prepay_id=xxx,序列化为 package */
    @JsonProperty("package")
    private String packageValue;

    /** 签名类型,V3 固定 RSA */
    private String signType;

    /** 签名 */
    private String paySign;
}
