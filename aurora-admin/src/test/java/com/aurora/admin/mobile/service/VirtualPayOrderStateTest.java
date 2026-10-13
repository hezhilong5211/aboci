package com.aurora.admin.mobile.service;

import com.aurora.admin.distribution.service.IDistributionFinanceService;
import com.aurora.admin.mobile.domain.PayOrder;
import com.aurora.admin.mobile.exception.MembershipRequiredException;
import com.aurora.admin.mobile.mapper.PayOrderMapper;
import com.aurora.admin.mobile.service.impl.PayOrderBizServiceImpl;
import com.aurora.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 虚拟支付订单终态不得被晚到回调重新履约。 */
class VirtualPayOrderStateTest {

    private static final String ORDER_NO = "20260710000000000001";

    private PayOrderBizServiceImpl service;
    private PayOrderMapper payOrderMapper;
    private IPayOrderService payOrderService;
    private IUserMembershipService userMembershipService;
    private IBeanAccountService beanAccountService;
    private IMembershipRefundEntitlementService membershipRefundEntitlementService;
    private IDistributionFinanceService distributionFinanceService;

    @BeforeEach
    void setUp() {
        payOrderMapper = mock(PayOrderMapper.class);
        payOrderService = mock(IPayOrderService.class);
        userMembershipService = mock(IUserMembershipService.class);
        beanAccountService = mock(IBeanAccountService.class);
        membershipRefundEntitlementService = mock(IMembershipRefundEntitlementService.class);
        distributionFinanceService = mock(IDistributionFinanceService.class);

        service = new PayOrderBizServiceImpl(
                mock(WxServiceFactory.class),
                payOrderService,
                mock(IMobileUserService.class),
                payOrderMapper,
                userMembershipService,
                beanAccountService,
                mock(IMembershipLevelService.class),
                membershipRefundEntitlementService,
                mock(IBeanRechargePackageService.class),
                mock(ISysWxConfigService.class),
                distributionFinanceService,
                mock(IPayOrderBizService.class));
    }

    @Test
    void closedVirtualOrderCannotBeRevivedByLateCallback() {
        PayOrder order = terminalVirtualOrder(3);
        when(payOrderMapper.findByOrderNoForUpdate(ORDER_NO)).thenReturn(order);

        assertThrows(ServiceException.class,
                () -> service.handleVirtualPaySuccess(ORDER_NO, "wx-order", "transaction", 199));

        verify(payOrderService, never()).updateById(order);
        verify(userMembershipService, never()).grantFromPayment(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyString());
        verify(beanAccountService, never()).recharge(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void refundedVirtualOrderIdempotentlyIgnoresLateGoodsCallback() {
        PayOrder order = terminalVirtualOrder(2);
        when(payOrderMapper.findByOrderNoForUpdate(ORDER_NO)).thenReturn(order);

        service.handleVirtualPaySuccess(ORDER_NO, "wx-order", "transaction", 199);

        verify(payOrderService, never()).updateById(order);
        verify(userMembershipService, never()).grantFromPayment(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void indivisibleVirtualGoodRejectsPartialRefund() {
        PayOrder order = terminalVirtualOrder(1);
        order.setAmount(299);
        when(payOrderMapper.findByOrderNoForUpdate(ORDER_NO)).thenReturn(order);

        assertThrows(ServiceException.class,
                () -> service.handleVirtualRefund(ORDER_NO, "refund-1", 100, null));

        verify(payOrderService, never()).updateById(order);
    }

    @Test
    void beanRechargeCallbackAfterMembershipExpiryDoesNotGrantBeans() {
        PayOrder order = terminalVirtualOrder(0);
        order.setUserId(1001L);
        order.setAmount(299);
        order.setProductType("BEAN_RECHARGE");
        order.setProductId(2L);
        order.setProductName("加油包");
        order.setSnapshotBeans(2000L);
        when(payOrderMapper.findByOrderNoForUpdate(ORDER_NO)).thenReturn(order);
        doThrow(new MembershipRequiredException("加油包仅限有效会员购买"))
                .when(userMembershipService).requireActiveMembership(1001L);

        service.handleVirtualPaySuccess(ORDER_NO, "wx-order", "transaction", 299);

        verify(userMembershipService).requireActiveMembership(1001L);
        verify(beanAccountService, never()).recharge(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
        assertEquals(4, order.getStatus());
        assertEquals(2, order.getFulfillmentStatus());
        assertEquals("支付完成时会员已到期，未发放加油包，请人工退款", order.getFulfillmentError());
        verify(payOrderService).updateById(order);
    }

    @Test
    void membershipFulfillmentUsesWechatPaidTimeAndSameBusinessZoneGrantTime() {
        PayOrder order = terminalVirtualOrder(0);
        order.setUserId(1001L);
        order.setProductType("MEMBERSHIP");
        order.setProductId(1L);
        order.setProductName("青铜会员");
        order.setSnapshotGrantBeans(2000L);
        order.setSnapshotLevelSort(1);
        order.setSnapshotDurationDays(90);
        when(payOrderMapper.findByOrderNoForUpdate(ORDER_NO)).thenReturn(order);
        LocalDateTime paidAt = MembershipPolicy.now().minusSeconds(5);

        service.handleVirtualPaySuccess(
                ORDER_NO, "wx-order", "transaction", 199, paidAt);

        ArgumentCaptor<LocalDateTime> grantAt = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(userMembershipService).grantFromPayment(
                org.mockito.ArgumentMatchers.eq(1001L),
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(1),
                org.mockito.ArgumentMatchers.eq("青铜会员"),
                org.mockito.ArgumentMatchers.eq(2000L),
                org.mockito.ArgumentMatchers.eq(new BigDecimal("1.99")),
                org.mockito.ArgumentMatchers.eq(90),
                org.mockito.ArgumentMatchers.eq(ORDER_NO),
                org.mockito.ArgumentMatchers.eq(paidAt),
                grantAt.capture());
        assertEquals(paidAt, order.getPayTime());
        assertEquals(false, grantAt.getValue().isBefore(paidAt));
        assertEquals(1, order.getStatus());
        verify(distributionFinanceService).recordPaidOrder(order);
    }

    @Test
    void pendingRefundOrderBecomesRefundedWithoutStaleManualRefundMessage() {
        PayOrder order = terminalVirtualOrder(4);
        order.setAmount(299);
        order.setFulfillmentStatus(2);
        order.setFulfillmentError("支付完成时会员已到期，未发放加油包，请人工退款");
        when(payOrderMapper.findByOrderNoForUpdate(ORDER_NO)).thenReturn(order);

        service.handleVirtualRefund(ORDER_NO, "refund-1", 299, null);

        assertEquals(2, order.getStatus());
        assertEquals("订单已退款，未发放权益", order.getFulfillmentError());
        verify(payOrderService).updateById(order);
    }

    @Test
    void refundedLastMembershipCancelsSegmentAndOnlyRevokesOwnRemainingGrant() {
        PayOrder order = terminalVirtualOrder(1);
        order.setUserId(1001L);
        order.setProductType("MEMBERSHIP");
        order.setSnapshotGrantBeans(2000L);
        when(payOrderMapper.findByOrderNoForUpdate(ORDER_NO)).thenReturn(order);
        LocalDateTime refundAt = LocalDateTime.of(2026, 7, 10, 12, 0);
        when(membershipRefundEntitlementService.cancelForRefund(
                org.mockito.ArgumentMatchers.eq(1001L),
                org.mockito.ArgumentMatchers.eq(ORDER_NO),
                org.mockito.ArgumentMatchers.eq(refundAt),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class)))
                .thenReturn(new MembershipRefundEntitlementResult(
                        true, true, 9L, 2000L, false, 0));

        service.handleVirtualRefund(ORDER_NO, "refund-member-1", 199, refundAt);

        verify(membershipRefundEntitlementService).cancelForRefund(
                org.mockito.ArgumentMatchers.eq(1001L),
                org.mockito.ArgumentMatchers.eq(ORDER_NO),
                org.mockito.ArgumentMatchers.eq(refundAt),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class));
        verify(beanAccountService).revokeForOrderRefund(
                org.mockito.ArgumentMatchers.eq(1001L),
                org.mockito.ArgumentMatchers.eq(2000L),
                org.mockito.ArgumentMatchers.eq(ORDER_NO),
                org.mockito.ArgumentMatchers.contains("尚未消费"));
        verify(beanAccountService, never()).revokeAllForOrderRefund(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
        assertEquals(2, order.getStatus());
        assertEquals("订单已退款，会员及豆子权益已自动撤销", order.getFulfillmentError());
        verify(payOrderService).updateById(order);
        verify(distributionFinanceService).reverseRefundedOrder(order, "refund-member-1", refundAt);
    }

    @Test
    void refundedRenewalKeepsOtherMembershipAndOnlyRevokesOwnRemainingGrant() {
        PayOrder order = terminalVirtualOrder(1);
        order.setUserId(1001L);
        order.setProductType("MEMBERSHIP");
        when(payOrderMapper.findByOrderNoForUpdate(ORDER_NO)).thenReturn(order);
        LocalDateTime refundAt = LocalDateTime.of(2026, 7, 10, 12, 0);
        when(membershipRefundEntitlementService.cancelForRefund(
                org.mockito.ArgumentMatchers.eq(1001L),
                org.mockito.ArgumentMatchers.eq(ORDER_NO),
                org.mockito.ArgumentMatchers.eq(refundAt),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class)))
                .thenReturn(new MembershipRefundEntitlementResult(
                        true, true, 10L, 2000L, true, 1));

        service.handleVirtualRefund(ORDER_NO, "refund-renewal-1", 199, refundAt);

        verify(beanAccountService).revokeForOrderRefund(
                org.mockito.ArgumentMatchers.eq(1001L),
                org.mockito.ArgumentMatchers.eq(2000L),
                org.mockito.ArgumentMatchers.eq(ORDER_NO),
                org.mockito.ArgumentMatchers.contains("尚未消费"));
        verify(beanAccountService, never()).revokeAllForOrderRefund(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    private PayOrder terminalVirtualOrder(int status) {
        PayOrder order = new PayOrder();
        order.setOrderNo(ORDER_NO);
        order.setPayChannel("VIRTUAL");
        order.setStatus(status);
        order.setAmount(199);
        return order;
    }
}
