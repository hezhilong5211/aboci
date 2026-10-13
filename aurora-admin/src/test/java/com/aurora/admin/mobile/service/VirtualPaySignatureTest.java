package com.aurora.admin.mobile.service;

import cn.binarywang.wx.miniapp.bean.xpay.WxMaXPaySigParams;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VirtualPaySignatureTest {

    @Test
    void matchesWechatOfficialHmacVectors() {
        String postBody = "{\"openid\": \"xxx\", \"user_ip\": \"127.0.0.1\", \"env\": 0}";
        WxMaXPaySigParams params = WxMaXPaySigParams.builder()
                .appKey("12345")
                .sessionKey("9hAb/NEYUlkaMBEsmFgzig==")
                .build();

        assertEquals("c37809f27c6d7fd1837ad2500a04512b66b34fd793a39a385fade56dca89a4b5",
                params.calcPaySig("/xpay/query_user_balance", postBody));
        assertEquals("089d9e8dc5d308977360c4b79ec600a93d736802802a807d634192328032f6c7",
                params.calcSig(postBody));
    }
}
