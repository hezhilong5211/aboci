package com.aurora.admin.mobile.util;

import com.aurora.common.core.exception.ServiceException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WxMessageCryptoTest {

    @Test
    void verifiesOfficialPlainSignatureVector() {
        assertTrue(WxMessageCrypto.verifyPlainSignature(
                "AAAAA", "1714036504", "1514711492",
                "f464b24fc39322e44b38aa78f5edd27bd1441696"));
    }

    @Test
    void decryptsOfficialSafeModeJsonVectorAndChecksAppId() {
        String encrypted = "+qdx1OKCy+5JPCBFWw70tm0fJGb2Jmeia4FCB7kao+/Q5c/ohsOzQHi8khUOb05JCpj0JB4RvQMkUyus8TPxLKJGQqcvZqzDpVzazhZv6JsXUnnR8XGT740XgXZUXQ7vJVnAG+tE8NUd4yFyjPy7GgiaviNrlCTj+l5kdfMuFUPpRSrfMZuMcp3Fn2Pede2IuQrKEYwKSqFIZoNqJ4M8EajAsjLY2km32IIjdf8YL/P50F7mStwntrA2cPDrM1kb6mOcfBgRtWygb3VIYnSeOBrebufAlr7F9mFUPAJGj04=";
        String plain = WxMessageCrypto.decryptJson(
                "AAAAA", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                "wxba5fad812f8e6fb9", "1714112445", "415670741",
                "046e02f8204d34f8ba5fa3b1db94908f3df2e9b3", encrypted);

        assertTrue(plain.contains("\"Event\":\"debug_demo\""));
        assertTrue(plain.contains("\"debug_str\":\"hello world\""));

        ServiceException error = assertThrows(ServiceException.class, () ->
                WxMessageCrypto.decryptJson(
                        "AAAAA", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                        "wx-wrong-appid", "1714112445", "415670741",
                        "046e02f8204d34f8ba5fa3b1db94908f3df2e9b3", encrypted));
        assertEquals("微信消息推送 AppId 不匹配", error.getMessage());
    }
}
