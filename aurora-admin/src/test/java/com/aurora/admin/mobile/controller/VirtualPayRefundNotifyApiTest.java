package com.aurora.admin.mobile.controller;

import com.aurora.admin.mobile.domain.SysWxConfig;
import com.aurora.admin.mobile.dto.VirtualPayRefundNotifyDTO;
import com.aurora.admin.mobile.service.ISysWxConfigService;
import com.aurora.admin.mobile.service.IVirtualPayService;
import com.aurora.framework.utils.EncryptUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 微信安全模式退款推送的 HTTP 验签、解密、DTO 路由测试。 */
class VirtualPayRefundNotifyApiTest {

    private static final String APP_ID = "wx6b90c35305bca0d7";
    private static final String TOKEN = "refund-push-token";
    private static final byte[] AES_KEY = "0123456789abcdef0123456789abcdef"
            .getBytes(StandardCharsets.UTF_8);
    private static final String AES_KEY_TEXT = Base64.getEncoder()
            .withoutPadding().encodeToString(AES_KEY);

    private IVirtualPayService virtualPayService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ISysWxConfigService configService = mock(ISysWxConfigService.class);
        virtualPayService = mock(IVirtualPayService.class);
        EncryptUtils encryptUtils = mock(EncryptUtils.class);
        ObjectMapper objectMapper = new ObjectMapper();

        SysWxConfig miniApp = new SysWxConfig();
        miniApp.setAppId(APP_ID);
        SysWxConfig virtualPay = new SysWxConfig();
        virtualPay.setAppId(APP_ID);
        virtualPay.setAppType("virtual_pay");
        virtualPay.setPushToken(TOKEN);
        virtualPay.setPushAesKey(AES_KEY_TEXT);
        when(configService.getActiveByType("mini_app")).thenReturn(miniApp);
        when(configService.getOne(any())).thenReturn(virtualPay);
        when(encryptUtils.decrypt(TOKEN)).thenReturn(TOKEN);
        when(encryptUtils.decrypt(AES_KEY_TEXT)).thenReturn(AES_KEY_TEXT);

        mockMvc = MockMvcBuilders.standaloneSetup(new VirtualPayNotifyController(
                configService, virtualPayService, encryptUtils, objectMapper)).build();
    }

    @Test
    void 正确安全模式退款报文返回success并路由退款DTO() throws Exception {
        String timestamp = "1783704000";
        String nonce = "refund-nonce-1";
        String plain = """
                {"Event":"xpay_refund_notify","OpenId":"openid-1",\
                "WxRefundId":"wx-refund-1","MchOrderId":"order-1",\
                "RefundFee":199,"RetCode":0,"RetMsg":"ok",\
                "RefundSuccTimestamp":1783703999}
                """.replace("\\\n", "");
        String encrypted = encrypt(plain, APP_ID);
        String signature = sha1(TOKEN, timestamp, nonce, encrypted);

        mockMvc.perform(post("/api/mobile/wx/msg-push")
                        .param("timestamp", timestamp)
                        .param("nonce", nonce)
                        .param("encrypt_type", "aes")
                        .param("msg_signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"Encrypt\":\"" + encrypted + "\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string("success"));

        ArgumentCaptor<VirtualPayRefundNotifyDTO> captor =
                ArgumentCaptor.forClass(VirtualPayRefundNotifyDTO.class);
        verify(virtualPayService).handleRefund(captor.capture());
        VirtualPayRefundNotifyDTO dto = captor.getValue();
        assertEquals("order-1", dto.getMchOrderId());
        assertEquals("wx-refund-1", dto.getWxRefundId());
        assertEquals(199, dto.getRefundFee());
        assertEquals(0, dto.getRetCode());
        assertEquals(1783703999L, dto.getRefundSuccTimestamp());
    }

    @Test
    void 错误签名返回503且不进入退款业务() throws Exception {
        String encrypted = encrypt("{\"Event\":\"xpay_refund_notify\"}", APP_ID);

        mockMvc.perform(post("/api/mobile/wx/msg-push")
                        .param("timestamp", "1783704000")
                        .param("nonce", "refund-nonce-2")
                        .param("encrypt_type", "aes")
                        .param("msg_signature", "invalid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"Encrypt\":\"" + encrypted + "\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string("fail"));

        verify(virtualPayService, never()).handleRefund(any());
    }

    private static String encrypt(String json, String appId) throws Exception {
        byte[] message = json.getBytes(StandardCharsets.UTF_8);
        byte[] app = appId.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(16 + 4 + message.length + app.length);
        buffer.put("0123456789abcdef".getBytes(StandardCharsets.UTF_8));
        buffer.putInt(message.length);
        buffer.put(message);
        buffer.put(app);
        byte[] plain = buffer.array();
        int padding = 32 - plain.length % 32;
        byte[] padded = Arrays.copyOf(plain, plain.length + padding);
        Arrays.fill(padded, plain.length, padded.length, (byte) padding);

        Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(AES_KEY, "AES"),
                new IvParameterSpec(AES_KEY, 0, 16));
        return Base64.getEncoder().encodeToString(cipher.doFinal(padded));
    }

    private static String sha1(String... values) throws Exception {
        String[] sorted = Arrays.copyOf(values, values.length);
        Arrays.sort(sorted);
        byte[] hash = MessageDigest.getInstance("SHA-1")
                .digest(String.join("", sorted).getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            hex.append(String.format("%02x", b & 0xff));
        }
        return hex.toString();
    }
}
