package com.aurora.admin.mobile.service;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.api.WxMaXPayService;
import cn.binarywang.wx.miniapp.bean.xpay.WxMaXPayQueryOrderResponse;
import cn.binarywang.wx.miniapp.config.WxMaConfig;
import cn.binarywang.wx.miniapp.constant.WxMaConstants;
import com.aurora.admin.mobile.domain.PayOrder;
import com.aurora.admin.mobile.domain.SysWxConfig;
import com.aurora.admin.mobile.dto.VirtualPayClientFailureDTO;
import com.aurora.admin.mobile.dto.VirtualPayGoodsNotifyDTO;
import com.aurora.admin.mobile.service.impl.VirtualPayServiceImpl;
import com.aurora.framework.utils.EncryptUtils;
import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.invocation.Invocation;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 微信虚拟支付时间语义与退款/发货竞态单测。 */
class VirtualPayPaidTimeTest {

    private static final String ORDER_NO = "20260710000000000088";
    private static final String APP_ID = "wx6b90c35305bca0d7";
    private static final long USER_ID = 1001L;
    private static final long PAID_TIME_SECONDS = 1_783_656_000L;
    private static final LocalDateTime SHANGHAI_PAID_TIME =
            LocalDateTime.of(2026, 7, 10, 12, 0);

    private ISysWxConfigService sysWxConfigService;
    private WxServiceFactory wxServiceFactory;
    private IPayOrderService payOrderService;
    private IPayOrderBizService payOrderBizService;
    private EncryptUtils encryptUtils;
    private VirtualPayServiceImpl service;

    @BeforeEach
    void setUp() {
        sysWxConfigService = mock(ISysWxConfigService.class);
        wxServiceFactory = mock(WxServiceFactory.class);
        payOrderService = mock(IPayOrderService.class);
        payOrderBizService = mock(IPayOrderBizService.class);
        encryptUtils = mock(EncryptUtils.class);
        service = new VirtualPayServiceImpl(
                sysWxConfigService,
                wxServiceFactory,
                mock(IMobileUserService.class),
                mock(IMembershipLevelService.class),
                mock(IBeanRechargePackageService.class),
                payOrderService,
                payOrderBizService,
                mock(IUserMembershipService.class),
                encryptUtils,
                mock(ObjectMapper.class));
    }

    @Test
    void 发货推送PaidTime按北京时间传播且不受JVM默认时区影响() {
        PayOrder pending = virtualOrder();
        PayOrder failed = virtualOrder();
        failed.setFulfillmentStatus(2);
        when(payOrderService.getOne(any())).thenReturn(pending, failed);

        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
            service.handleGoodsDeliver(goodsNotify());
        } finally {
            TimeZone.setDefault(original);
        }

        verify(payOrderBizService).handleVirtualPaySuccess(
                ORDER_NO, null, "transaction-88", 199, SHANGHAI_PAID_TIME);
    }

    @Test
    void 主动查单PaidTime同样按北京时间传播且不受JVM默认时区影响() throws Exception {
        PayOrder pending = virtualOrder();
        pending.setStatus(0);
        PayOrder failed = virtualOrder();
        failed.setStatus(4);
        failed.setFulfillmentStatus(2);
        when(payOrderService.getOne(any())).thenReturn(pending, failed);

        SysWxConfig miniApp = new SysWxConfig();
        miniApp.setAppId(APP_ID);
        SysWxConfig virtualPay = new SysWxConfig();
        virtualPay.setAppId(APP_ID);
        virtualPay.setOfferId("offer-1");
        virtualPay.setEnv(0);
        virtualPay.setProdAppKey("encrypted-app-key");
        when(sysWxConfigService.getActiveByType("mini_app")).thenReturn(miniApp);
        when(sysWxConfigService.getOne(any())).thenReturn(virtualPay);
        when(encryptUtils.decrypt("encrypted-app-key")).thenReturn("plain-app-key");

        WxMaService maService = mock(WxMaService.class);
        WxMaConfig maConfig = mock(WxMaConfig.class);
        WxMaXPayService xPayService = mock(WxMaXPayService.class);
        when(wxServiceFactory.getWxMaService()).thenReturn(maService);
        when(maService.getWxMaConfig()).thenReturn(maConfig);
        when(maConfig.getAppid()).thenReturn(APP_ID);
        when(maService.getWxMaXPayService()).thenReturn(xPayService);

        WxMaXPayQueryOrderResponse.OrderInfo info = new WxMaXPayQueryOrderResponse.OrderInfo();
        info.setStatus(WxMaConstants.XPayOrderStatus.PROVIDED);
        info.setOrderFee(199L);
        info.setPaidFee(199L);
        info.setPaidTime(PAID_TIME_SECONDS);
        info.setWxOrderId("wx-order-88");
        info.setWxpayOrderId("transaction-88");
        WxMaXPayQueryOrderResponse response = new WxMaXPayQueryOrderResponse();
        response.setOrder(info);
        when(xPayService.queryOrder(any(), any())).thenReturn(response);

        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
            service.query(USER_ID, ORDER_NO, true);
        } finally {
            TimeZone.setDefault(original);
        }

        verify(payOrderBizService).handleVirtualPaySuccess(
                ORDER_NO, "wx-order-88", "transaction-88", 199, SHANGHAI_PAID_TIME);
    }

    @Test
    void 客户端失败上报只读取本人订单且不改变支付状态() {
        PayOrder order = virtualOrder();
        order.setProductType("BEAN_RECHARGE");
        order.setProductId(2L);
        when(payOrderService.getOne(any())).thenReturn(order);

        VirtualPayClientFailureDTO dto = new VirtualPayClientFailureDTO();
        dto.setOrderNo(ORDER_NO);
        dto.setErrCode("-4");
        dto.setErrMsg("requestVirtualPayment:fail risk control\nforged-log");
        dto.setRawError("{\"errCode\":-4}");
        dto.setProductType("BEAN_RECHARGE");
        dto.setProductId(2L);
        dto.setPlatform("ios");

        service.reportClientFailure(USER_ID, dto);

        verify(payOrderService).getOne(any());
        verify(payOrderService, never()).save(any());
        verify(payOrderBizService, never()).handleVirtualPaySuccess(any(), any(), any(), any(), any());
    }

    @Test
    void 客户端失败上报拒绝非订单用户() {
        when(payOrderService.getOne(any())).thenReturn(virtualOrder());
        VirtualPayClientFailureDTO dto = new VirtualPayClientFailureDTO();
        dto.setOrderNo(ORDER_NO);
        dto.setErrCode("-4");

        assertThrows(RuntimeException.class,
                () -> service.reportClientFailure(USER_ID + 1, dto));
    }

    @Test
    void 已履约订单主动查单可补偿丢失退款推送且五分钟内不重复调微信() throws Exception {
        PayOrder fulfilled = virtualOrder();
        fulfilled.setStatus(1);
        fulfilled.setFulfillmentStatus(1);
        fulfilled.setDeliveryConfirmed(1);
        fulfilled.setWxOrderId("wx-order-88");
        when(payOrderService.getOne(any())).thenReturn(fulfilled);

        SysWxConfig miniApp = new SysWxConfig();
        miniApp.setAppId(APP_ID);
        SysWxConfig virtualPay = new SysWxConfig();
        virtualPay.setAppId(APP_ID);
        virtualPay.setOfferId("offer-1");
        virtualPay.setEnv(0);
        virtualPay.setProdAppKey("encrypted-app-key");
        when(sysWxConfigService.getActiveByType("mini_app")).thenReturn(miniApp);
        when(sysWxConfigService.getOne(any())).thenReturn(virtualPay);
        when(encryptUtils.decrypt("encrypted-app-key")).thenReturn("plain-app-key");

        WxMaService maService = mock(WxMaService.class);
        WxMaConfig maConfig = mock(WxMaConfig.class);
        WxMaXPayService xPayService = mock(WxMaXPayService.class);
        when(wxServiceFactory.getWxMaService()).thenReturn(maService);
        when(maService.getWxMaConfig()).thenReturn(maConfig);
        when(maConfig.getAppid()).thenReturn(APP_ID);
        when(maService.getWxMaXPayService()).thenReturn(xPayService);

        WxMaXPayQueryOrderResponse.OrderInfo info = new WxMaXPayQueryOrderResponse.OrderInfo();
        info.setStatus(WxMaConstants.XPayOrderStatus.REFUNDED);
        info.setRefundFee(199L);
        WxMaXPayQueryOrderResponse response = new WxMaXPayQueryOrderResponse();
        response.setOrder(info);
        when(xPayService.queryOrder(any(), any())).thenReturn(response);

        service.query(USER_ID, ORDER_NO, true);
        service.query(USER_ID, ORDER_NO, true);

        verify(payOrderBizService).handleVirtualRefund(
                eq(ORDER_NO), isNull(), eq(199), any(LocalDateTime.class));
        verify(xPayService, times(1)).queryOrder(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void 退款竞态_确认发货更新必须限定订单仍为已支付且权益成功() throws Exception {
        LambdaUpdateChainWrapper<PayOrder> update =
                mock(LambdaUpdateChainWrapper.class, Answers.RETURNS_SELF);
        when(payOrderService.lambdaUpdate()).thenReturn(update);
        doReturn(update).when(update).eq(any(SFunction.class), any());
        doReturn(update).when(update).set(any(SFunction.class), any());

        Method method = VirtualPayServiceImpl.class
                .getDeclaredMethod("markDeliveryConfirmed", Long.class);
        method.setAccessible(true);
        method.invoke(service, 88L);

        assertTrue(hasColumnInvocation(update, "eq", "getId", 88L),
                "确认发货必须只更新目标订单");
        assertTrue(hasColumnInvocation(update, "eq", "getStatus", 1),
                "退款已将 status 改为 2 后，旧发货请求必须更新不到该订单");
        assertTrue(hasColumnInvocation(update, "eq", "getFulfillmentStatus", 1),
                "只允许已成功发放权益的订单确认发货");
        assertTrue(hasColumnInvocation(update, "eq", "getDeliveryConfirmed", 0),
                "发货确认必须幂等");
        assertTrue(hasColumnInvocation(update, "set", "getDeliveryConfirmed", 1),
                "满足全部原子条件时才写入发货确认");
        verify(update).update();
    }

    private PayOrder virtualOrder() {
        PayOrder order = new PayOrder();
        order.setId(88L);
        order.setOrderNo(ORDER_NO);
        order.setUserId(USER_ID);
        order.setOpenId("openid-88");
        order.setAmount(199);
        order.setPayChannel("VIRTUAL");
        order.setEnv(0);
        order.setVirtualProductId("bronze_member");
        order.setStatus(0);
        order.setFulfillmentStatus(0);
        order.setDeliveryConfirmed(0);
        return order;
    }

    private VirtualPayGoodsNotifyDTO goodsNotify() {
        VirtualPayGoodsNotifyDTO notify = new VirtualPayGoodsNotifyDTO();
        notify.setEvent("xpay_goods_deliver_notify");
        notify.setOpenId("openid-88");
        notify.setOutTradeNo(ORDER_NO);
        notify.setEnv(0);

        VirtualPayGoodsNotifyDTO.WeChatPayInfo payInfo =
                new VirtualPayGoodsNotifyDTO.WeChatPayInfo();
        payInfo.setTransactionId("transaction-88");
        payInfo.setPaidTime(PAID_TIME_SECONDS);
        notify.setWeChatPayInfo(payInfo);

        VirtualPayGoodsNotifyDTO.GoodsInfo goods = new VirtualPayGoodsNotifyDTO.GoodsInfo();
        goods.setProductId("bronze_member");
        goods.setQuantity(1);
        goods.setOrigPrice(199);
        goods.setActualPrice(199);
        goods.setAttach(ORDER_NO);
        notify.setGoodsInfo(goods);
        return notify;
    }

    private boolean hasColumnInvocation(LambdaUpdateChainWrapper<PayOrder> update,
                                        String methodName, String getterName, Object value) {
        for (Invocation invocation : mockingDetails(update).getInvocations()) {
            if (!methodName.equals(invocation.getMethod().getName())) {
                continue;
            }
            Object[] arguments = invocation.getArguments();
            int columnIndex = arguments.length > 0 && arguments[0] instanceof Boolean ? 1 : 0;
            int valueIndex = columnIndex + 1;
            if (arguments.length <= valueIndex || !(arguments[columnIndex] instanceof SFunction<?, ?>)) {
                continue;
            }
            @SuppressWarnings("rawtypes")
            SFunction column = (SFunction) arguments[columnIndex];
            if (getterName.equals(LambdaUtils.extract(column).getImplMethodName())
                    && Objects.equals(value, arguments[valueIndex])) {
                return true;
            }
        }
        return false;
    }
}
