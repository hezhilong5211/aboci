package com.aurora.admin.mobile.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 微信小程序 requestVirtualPayment 调起参数。
 * signData 必须由客户端原样传给微信，禁止解析后重新序列化。
 */
@Data
public class VirtualPayCreateVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String orderNo;
    private String mode;
    private String signData;
    private String paySig;
    private String signature;
    private Integer env;
}
