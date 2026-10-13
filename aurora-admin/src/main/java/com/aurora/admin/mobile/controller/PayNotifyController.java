package com.aurora.admin.mobile.controller;

import com.aurora.admin.mobile.service.IPayOrderBizService;
import com.aurora.admin.mobile.service.WxServiceFactory;
import com.github.binarywang.wxpay.bean.notify.SignatureHeader;
import com.github.binarywang.wxpay.bean.notify.WxPayNotifyV3Response;
import com.github.binarywang.wxpay.bean.notify.WxPayNotifyV3Result;
import com.github.binarywang.wxpay.service.WxPayService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.util.json.WxGsonBuilder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 微信支付回调(免鉴权)
 * parseOrderNotifyV3Result 内部已自动验签 + AES-256-GCM 解密;
 * 处理成功返回 {"code":"SUCCESS"} + HTTP 200,否则返回 FAIL,微信按策略重试最长 24h。
 */
@Slf4j
@RestController
@RequestMapping("/api/pay")
@RequiredArgsConstructor
public class PayNotifyController {

    private final WxServiceFactory wxServiceFactory;
    private final IPayOrderBizService payOrderBizService;

    /**
     * 支付结果回调。微信 V3 为 JSON,用原始 body 验签(切勿被过滤器改写)。
     */
    @PostMapping("/notify")
    public String notify(@RequestBody String body, HttpServletRequest request) {
        SignatureHeader header = new SignatureHeader();
        header.setSignature(request.getHeader("Wechatpay-Signature"));
        header.setNonce(request.getHeader("Wechatpay-Nonce"));
        header.setTimeStamp(request.getHeader("Wechatpay-Timestamp"));
        header.setSerial(request.getHeader("Wechatpay-Serial"));
        try {
            WxPayService wxPayService = wxServiceFactory.getWxPayService();
            // 内部自动验签 + 解密
            WxPayNotifyV3Result result = wxPayService.parseOrderNotifyV3Result(body, header);
            WxPayNotifyV3Result.DecryptNotifyResult r = result.getResult();
            payOrderBizService.handlePaySuccess(
                    r.getOutTradeNo(), r.getTransactionId(),
                    r.getTradeState(), r.getAmount().getTotal());
            return WxGsonBuilder.create().toJson(WxPayNotifyV3Response.success("成功"));
        } catch (Exception e) {
            // 验签/解密/业务失败 → 返回 FAIL,微信按策略重试
            log.warn("支付回调处理失败:{}", e.getMessage());
            return WxGsonBuilder.create().toJson(WxPayNotifyV3Response.fail(e.getMessage()));
        }
    }
}
