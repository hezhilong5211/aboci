package com.aurora.admin.mobile.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * 微信虚拟支付道具发货推送明文。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class VirtualPayGoodsNotifyDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("ToUserName")
    private String toUserName;

    @JsonProperty("FromUserName")
    private String fromUserName;

    @JsonProperty("CreateTime")
    private Long createTime;

    @JsonProperty("MsgType")
    private String msgType;

    @JsonProperty("Event")
    private String event;

    @JsonProperty("OpenId")
    private String openId;

    @JsonProperty("OutTradeNo")
    private String outTradeNo;

    @JsonProperty("Env")
    private Integer env;

    @JsonProperty("WeChatPayInfo")
    private WeChatPayInfo weChatPayInfo;

    @JsonProperty("GoodsInfo")
    private GoodsInfo goodsInfo;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WeChatPayInfo implements Serializable {
        private static final long serialVersionUID = 1L;

        @JsonProperty("MchOrderNo")
        private String mchOrderNo;

        @JsonProperty("TransactionId")
        private String transactionId;

        @JsonProperty("PaidTime")
        private Long paidTime;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GoodsInfo implements Serializable {
        private static final long serialVersionUID = 1L;

        @JsonProperty("ProductId")
        private String productId;

        @JsonProperty("Quantity")
        private Integer quantity;

        @JsonProperty("OrigPrice")
        private Integer origPrice;

        @JsonProperty("ActualPrice")
        private Integer actualPrice;

        @JsonProperty("Attach")
        private String attach;
    }
}
