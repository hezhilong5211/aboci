package com.aurora.admin.mobile.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;

/** 微信虚拟支付退款结果推送明文。 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class VirtualPayRefundNotifyDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("Event")
    private String event;

    @JsonProperty("OpenId")
    private String openId;

    @JsonProperty("WxRefundId")
    private String wxRefundId;

    @JsonProperty("MchRefundId")
    private String mchRefundId;

    @JsonProperty("WxOrderId")
    private String wxOrderId;

    @JsonProperty("MchOrderId")
    private String mchOrderId;

    @JsonProperty("RefundFee")
    private Integer refundFee;

    @JsonProperty("RetCode")
    private Integer retCode;

    @JsonProperty("RetMsg")
    private String retMsg;

    @JsonProperty("RefundSuccTimestamp")
    private Long refundSuccTimestamp;
}
