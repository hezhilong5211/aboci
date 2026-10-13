package com.aurora.admin.mobile.controller;

import com.aurora.admin.mobile.domain.SysWxConfig;
import com.aurora.admin.mobile.dto.VirtualPayGoodsNotifyDTO;
import com.aurora.admin.mobile.dto.VirtualPayRefundNotifyDTO;
import com.aurora.admin.mobile.service.ISysWxConfigService;
import com.aurora.admin.mobile.service.IVirtualPayService;
import com.aurora.admin.mobile.util.WxMessageCrypto;
import com.aurora.common.core.exception.ServiceException;
import com.aurora.framework.utils.EncryptUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 微信小程序统一消息推送入口。
 *
 * <p>该接口免 JWT，但会按微信 Token 验签；安全模式还会校验 msg_signature、AES 解密和 AppId。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/mobile/wx/msg-push")
@RequiredArgsConstructor
public class VirtualPayNotifyController {

    private static final String EVENT_GOODS_DELIVER = "xpay_goods_deliver_notify";
    private static final String EVENT_REFUND = "xpay_refund_notify";

    private final ISysWxConfigService sysWxConfigService;
    private final IVirtualPayService virtualPayService;
    private final EncryptUtils encryptUtils;
    private final ObjectMapper objectMapper;

    /** 微信公众平台保存消息推送配置时的 URL 验证。 */
    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> verify(
            @RequestParam String signature,
            @RequestParam String timestamp,
            @RequestParam String nonce,
            @RequestParam String echostr) {
        try {
            SysWxConfig config = getConfig();
            String token = decryptRequired(config.getPushToken());
            if (!WxMessageCrypto.verifyPlainSignature(token, timestamp, nonce, signature)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("invalid signature");
            }
            return ResponseEntity.ok(echostr);
        } catch (Exception e) {
            log.warn("微信消息推送 URL 验证失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("unavailable");
        }
    }

    /** 接收 JSON 格式的安全模式消息推送；虚拟支付不接受明文回调。 */
    @PostMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> push(
            @RequestBody String body,
            @RequestParam String timestamp,
            @RequestParam String nonce,
            @RequestParam(name = "encrypt_type", required = false) String encryptType,
            @RequestParam(name = "msg_signature", required = false) String msgSignature) {
        try {
            SysWxConfig config = getConfig();
            String token = decryptRequired(config.getPushToken());
            if (!"aes".equalsIgnoreCase(encryptType) || msgSignature == null || msgSignature.isBlank()) {
                throw new ServiceException("虚拟支付消息推送仅接受安全模式");
            }
            JsonNode encryptedBody = objectMapper.readTree(body);
            String encrypted = encryptedBody.path("Encrypt").asText(null);
            String plainJson = WxMessageCrypto.decryptJson(token,
                    decryptRequired(config.getPushAesKey()), config.getAppId(),
                    timestamp, nonce, msgSignature, encrypted);

            JsonNode message = objectMapper.readTree(plainJson);
            String event = message.path("Event").asText("");
            if (EVENT_GOODS_DELIVER.equals(event)) {
                virtualPayService.handleGoodsDeliver(
                        objectMapper.treeToValue(message, VirtualPayGoodsNotifyDTO.class));
            } else if (EVENT_REFUND.equals(event)) {
                virtualPayService.handleRefund(
                        objectMapper.treeToValue(message, VirtualPayRefundNotifyDTO.class));
            }
            // 官方允许 success/空串作为成功应答；安全模式下 success 无需再次加密。
            return ResponseEntity.ok("success");
        } catch (Exception e) {
            // 非 2xx 让微信按官方策略重推（最多 15 次），不得回显内部异常或原始报文。
            log.error("微信虚拟支付消息处理失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("fail");
        }
    }

    private SysWxConfig getConfig() {
        SysWxConfig miniApp = sysWxConfigService.getActiveByType("mini_app");
        if (miniApp == null || miniApp.getAppId() == null || miniApp.getAppId().isBlank()) {
            throw new ServiceException("已启用的小程序配置不存在");
        }
        SysWxConfig config = sysWxConfigService.getOne(new LambdaQueryWrapper<SysWxConfig>()
                .eq(SysWxConfig::getAppType, "virtual_pay")
                .eq(SysWxConfig::getAppId, miniApp.getAppId())
                .orderByDesc(SysWxConfig::getIsActive)
                .orderByDesc(SysWxConfig::getUpdateTime)
                .last("LIMIT 1"));
        if (config == null || config.getAppId() == null || config.getAppId().isBlank()) {
            throw new ServiceException("虚拟支付消息推送配置不存在");
        }
        return config;
    }

    private String decryptRequired(String value) {
        if (value == null || value.isBlank()) {
            throw new ServiceException("虚拟支付消息推送密钥未配置");
        }
        String plain = encryptUtils.decrypt(value);
        if (plain == null || plain.isBlank() || plain.startsWith("ENC:") || plain.startsWith("ENC2:")) {
            throw new ServiceException("虚拟支付消息推送密钥不可用");
        }
        return plain;
    }
}
