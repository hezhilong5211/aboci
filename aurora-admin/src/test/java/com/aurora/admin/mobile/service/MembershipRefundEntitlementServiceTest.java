package com.aurora.admin.mobile.service;

import com.aurora.admin.mobile.domain.UserMembership;
import com.aurora.admin.mobile.mapper.UserMembershipMapper;
import com.aurora.admin.mobile.service.impl.MembershipRefundEntitlementServiceImpl;
import com.aurora.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会员退款权益单测：精确取消、左闭右开边界、续费链压缩与幂等。
 */
class MembershipRefundEntitlementServiceTest {

    private static final long USER_ID = 1001L;

    private IBeanAccountService beanAccountService;
    private UserMembershipMapper userMembershipMapper;
    private MembershipRefundEntitlementServiceImpl service;

    @BeforeEach
    void setUp() {
        beanAccountService = mock(IBeanAccountService.class);
        userMembershipMapper = mock(UserMembershipMapper.class);
        service = new MembershipRefundEntitlementServiceImpl(
                beanAccountService, userMembershipMapper);
    }

    @Test
    void 按sourceOrderNo精确取消_返回赠豆快照与取消后会员状态() {
        LocalDateTime refundAt = LocalDateTime.of(2026, 7, 10, 12, 0);
        UserMembership target = segment(
                11L, "refund-order", refundAt.minusDays(10), refundAt.plusDays(20), "0", 2000L);
        UserMembership anotherActive = segment(
                12L, "keep-order", refundAt.minusDays(5), refundAt.plusDays(25), "0", 500L);
        when(userMembershipMapper.selectByUserIdForUpdate(USER_ID))
                .thenReturn(List.of(target, anotherActive));
        when(userMembershipMapper.cancelForRefund(11L, "refund-order", refundAt))
                .thenReturn(1);

        MembershipRefundEntitlementResult result = service.cancelForRefund(
                USER_ID, "refund-order", refundAt, refundAt);

        assertTrue(result.targetFound());
        assertTrue(result.cancelledNow());
        assertEquals(11L, result.cancelledMembershipId());
        assertEquals(2000L, result.cancelledGrantBeans());
        assertTrue(result.hasActiveMembershipAfter());
        assertEquals(0, result.compactedFutureSegmentCount());
        assertEquals("2", target.getStatus());
        verify(userMembershipMapper).cancelForRefund(11L, "refund-order", refundAt);
        verify(userMembershipMapper, never()).cancelForRefund(
                eq(12L), anyString(), any(LocalDateTime.class));

        InOrder lockOrder = inOrder(beanAccountService, userMembershipMapper);
        lockOrder.verify(beanAccountService).lockAccount(USER_ID);
        lockOrder.verify(userMembershipMapper).selectByUserIdForUpdate(USER_ID);
    }

    @Test
    void 退未生效中间段_后续段前移到当前会员到期时刻() {
        LocalDateTime refundAt = LocalDateTime.of(2026, 7, 10, 12, 0);
        LocalDateTime currentExpire = refundAt.plusDays(10);
        UserMembership current = segment(
                1L, "current", currentExpire.minusDays(MembershipPolicy.DURATION_DAYS),
                currentExpire, "0", 2000L);
        UserMembership refundedFuture = segment(
                2L, "refund-future", currentExpire,
                currentExpire.plusDays(MembershipPolicy.DURATION_DAYS), "0", 2000L);
        UserMembership keptFuture = segment(
                3L, "keep-future", currentExpire.plusDays(MembershipPolicy.DURATION_DAYS),
                currentExpire.plusDays(MembershipPolicy.DURATION_DAYS * 2L), "0", 2000L);
        keptFuture.setPaidTime(refundAt.minusDays(1));
        when(userMembershipMapper.selectByUserIdForUpdate(USER_ID))
                .thenReturn(List.of(current, refundedFuture, keptFuture));
        when(userMembershipMapper.cancelForRefund(2L, "refund-future", refundAt))
                .thenReturn(1);
        when(userMembershipMapper.rescheduleValidSegment(
                3L, USER_ID, currentExpire,
                currentExpire.plusDays(MembershipPolicy.DURATION_DAYS), refundAt))
                .thenReturn(1);

        MembershipRefundEntitlementResult result = service.cancelForRefund(
                USER_ID, "refund-future", refundAt, refundAt);

        assertEquals(1, result.compactedFutureSegmentCount());
        assertTrue(result.hasActiveMembershipAfter());
        assertEquals(currentExpire, keptFuture.getStartTime());
        assertEquals(currentExpire.plusDays(MembershipPolicy.DURATION_DAYS), keptFuture.getExpireTime());
        verify(userMembershipMapper).rescheduleValidSegment(
                3L, USER_ID, currentExpire,
                currentExpire.plusDays(MembershipPolicy.DURATION_DAYS), refundAt);
    }

    @Test
    void 退当前段_剩余两个续费段从退款时刻起连续压缩且每段365天() {
        LocalDateTime refundAt = LocalDateTime.of(2026, 7, 10, 12, 0);
        LocalDateTime oldExpire = refundAt.plusDays(5);
        UserMembership refundedCurrent = segment(
                1L, "refund-current", oldExpire.minusDays(MembershipPolicy.DURATION_DAYS),
                oldExpire, "0", 2000L);
        UserMembership renewal1 = segment(
                2L, "renewal-1", oldExpire,
                oldExpire.plusDays(MembershipPolicy.DURATION_DAYS), "0", 2000L);
        UserMembership renewal2 = segment(
                3L, "renewal-2", oldExpire.plusDays(MembershipPolicy.DURATION_DAYS),
                oldExpire.plusDays(MembershipPolicy.DURATION_DAYS * 2L), "0", 2000L);
        renewal1.setPaidTime(refundAt.minusDays(2));
        renewal2.setPaidTime(refundAt.minusDays(1));
        when(userMembershipMapper.selectByUserIdForUpdate(USER_ID))
                .thenReturn(List.of(refundedCurrent, renewal1, renewal2));
        when(userMembershipMapper.cancelForRefund(1L, "refund-current", refundAt))
                .thenReturn(1);
        when(userMembershipMapper.rescheduleValidSegment(anyLong(), eq(USER_ID),
                any(LocalDateTime.class), any(LocalDateTime.class), eq(refundAt)))
                .thenReturn(1);

        MembershipRefundEntitlementResult result = service.cancelForRefund(
                USER_ID, "refund-current", refundAt, refundAt);

        assertEquals(2, result.compactedFutureSegmentCount());
        assertTrue(result.hasActiveMembershipAfter(),
                "第一个未退续费段应在退款时刻立即接续");
        assertEquals(refundAt, renewal1.getStartTime());
        assertEquals(refundAt.plusDays(MembershipPolicy.DURATION_DAYS), renewal1.getExpireTime());
        assertEquals(refundAt.plusDays(MembershipPolicy.DURATION_DAYS), renewal2.getStartTime());
        assertEquals(refundAt.plusDays(MembershipPolicy.DURATION_DAYS * 2L), renewal2.getExpireTime());
    }

    @Test
    void 退当前段_后续30天和90天会员前移后各自时长不变() {
        LocalDateTime refundAt = LocalDateTime.of(2026, 7, 10, 12, 0);
        LocalDateTime oldExpire = refundAt.plusDays(5);
        UserMembership refundedCurrent = segment(
                1L, "refund-current-mixed", oldExpire.minusDays(365),
                oldExpire, "0", 2000L);
        UserMembership thirtyDays = segment(
                2L, "renewal-30", oldExpire,
                oldExpire.plusDays(30), "0", 2000L);
        UserMembership ninetyDays = segment(
                3L, "renewal-90", oldExpire.plusDays(30),
                oldExpire.plusDays(120), "0", 2000L);
        thirtyDays.setPaidTime(refundAt.minusDays(2));
        ninetyDays.setPaidTime(refundAt.minusDays(1));
        when(userMembershipMapper.selectByUserIdForUpdate(USER_ID))
                .thenReturn(List.of(refundedCurrent, thirtyDays, ninetyDays));
        when(userMembershipMapper.cancelForRefund(
                1L, "refund-current-mixed", refundAt)).thenReturn(1);
        when(userMembershipMapper.rescheduleValidSegment(anyLong(), eq(USER_ID),
                any(LocalDateTime.class), any(LocalDateTime.class), eq(refundAt)))
                .thenReturn(1);

        MembershipRefundEntitlementResult result = service.cancelForRefund(
                USER_ID, "refund-current-mixed", refundAt, refundAt);

        assertEquals(2, result.compactedFutureSegmentCount());
        assertEquals(refundAt, thirtyDays.getStartTime());
        assertEquals(refundAt.plusDays(30), thirtyDays.getExpireTime());
        assertEquals(refundAt.plusDays(30), ninetyDays.getStartTime());
        assertEquals(refundAt.plusDays(120), ninetyDays.getExpireTime());
    }

    @Test
    void 历史会员段缺真实支付时间_退款后保守保留原排期不前移() {
        LocalDateTime refundAt = LocalDateTime.of(2026, 7, 10, 12, 0);
        LocalDateTime oldExpire = refundAt.plusDays(5);
        UserMembership refundedCurrent = segment(
                1L, "refund-current", oldExpire.minusDays(MembershipPolicy.DURATION_DAYS),
                oldExpire, "0", 2000L);
        UserMembership historicalFuture = segment(
                2L, "history-without-paid-time", oldExpire,
                oldExpire.plusDays(MembershipPolicy.DURATION_DAYS), "0", 2000L);
        assertNull(historicalFuture.getPaidTime());
        when(userMembershipMapper.selectByUserIdForUpdate(USER_ID))
                .thenReturn(List.of(refundedCurrent, historicalFuture));
        when(userMembershipMapper.cancelForRefund(1L, "refund-current", refundAt))
                .thenReturn(1);

        MembershipRefundEntitlementResult result = service.cancelForRefund(
                USER_ID, "refund-current", refundAt, refundAt);

        assertEquals(0, result.compactedFutureSegmentCount());
        assertEquals(oldExpire, historicalFuture.getStartTime());
        assertEquals(oldExpire.plusDays(MembershipPolicy.DURATION_DAYS), historicalFuture.getExpireTime());
        verify(userMembershipMapper, never()).rescheduleValidSegment(
                anyLong(), anyLong(), any(), any(), any());
    }

    @Test
    void 有效边界严格为start小于等于refundAt且refundAt小于expire() {
        LocalDateTime refundAt = LocalDateTime.of(2026, 7, 10, 12, 0);
        UserMembership expiresAtBoundary = segment(
                1L, "expired-boundary", refundAt.minusDays(MembershipPolicy.DURATION_DAYS),
                refundAt, "0", 100L);
        UserMembership startsAtBoundary = segment(
                2L, "active-boundary", refundAt,
                refundAt.plusDays(MembershipPolicy.DURATION_DAYS), "0", 200L);
        when(userMembershipMapper.selectByUserIdForUpdate(USER_ID))
                .thenReturn(List.of(expiresAtBoundary, startsAtBoundary));

        MembershipRefundEntitlementResult result = service.cancelForRefund(
                USER_ID, "missing-order", refundAt, refundAt);

        assertFalse(result.targetFound());
        assertTrue(result.hasActiveMembershipAfter(),
                "start == refundAt 生效，expire == refundAt 已失效");
        verify(userMembershipMapper, never()).cancelForRefund(
                anyLong(), anyString(), any(LocalDateTime.class));
    }

    @Test
    void 目标段不存在时幂等安全返回_不取消也不移动其他权益() {
        LocalDateTime refundAt = LocalDateTime.of(2026, 7, 10, 12, 0);
        UserMembership unrelatedFuture = segment(
                2L, "other-order", refundAt.plusDays(10), refundAt.plusDays(40), "0", 500L);
        when(userMembershipMapper.selectByUserIdForUpdate(USER_ID))
                .thenReturn(List.of(unrelatedFuture));

        MembershipRefundEntitlementResult result = service.cancelForRefund(
                USER_ID, "not-found", refundAt, refundAt);

        assertFalse(result.targetFound());
        assertFalse(result.cancelledNow());
        assertNull(result.cancelledMembershipId());
        assertEquals(0L, result.cancelledGrantBeans());
        assertFalse(result.hasActiveMembershipAfter());
        assertEquals(0, result.compactedFutureSegmentCount());
        assertEquals(refundAt.plusDays(10), unrelatedFuture.getStartTime());
        verify(userMembershipMapper, never()).cancelForRefund(
                anyLong(), anyString(), any(LocalDateTime.class));
        verify(userMembershipMapper, never()).rescheduleValidSegment(
                anyLong(), anyLong(), any(), any(), any());
    }

    @Test
    void 目标已取消时不重复取消_仍返回原段赠豆快照() {
        LocalDateTime refundAt = LocalDateTime.of(2026, 7, 10, 12, 0);
        UserMembership cancelled = segment(
                1L, "refund-order", refundAt.minusDays(1), refundAt.plusDays(29), "2", 2000L);
        when(userMembershipMapper.selectByUserIdForUpdate(USER_ID))
                .thenReturn(List.of(cancelled));

        MembershipRefundEntitlementResult result = service.cancelForRefund(
                USER_ID, "refund-order", refundAt, refundAt);

        assertTrue(result.targetFound());
        assertFalse(result.cancelledNow());
        assertEquals(2000L, result.cancelledGrantBeans());
        assertFalse(result.hasActiveMembershipAfter());
        verify(userMembershipMapper, never()).cancelForRefund(
                anyLong(), anyString(), any(LocalDateTime.class));
    }

    @Test
    void 退已过期历史段_不倒推后来新购的当前或未来会员() {
        LocalDateTime refundAt = LocalDateTime.of(2026, 7, 10, 12, 0);
        UserMembership expiredTarget = segment(
                1L, "old-refund", refundAt.minusDays(60), refundAt.minusDays(30), "1", 2000L);
        UserMembership current = segment(
                2L, "new-current", refundAt.minusDays(2),
                refundAt.plusDays(MembershipPolicy.DURATION_DAYS - 2L), "0", 2000L);
        UserMembership future = segment(
                3L, "new-future", refundAt.plusDays(MembershipPolicy.DURATION_DAYS),
                refundAt.plusDays(MembershipPolicy.DURATION_DAYS * 2L), "0", 2000L);
        when(userMembershipMapper.selectByUserIdForUpdate(USER_ID))
                .thenReturn(List.of(expiredTarget, current, future));
        when(userMembershipMapper.cancelForRefund(1L, "old-refund", refundAt))
                .thenReturn(1);

        MembershipRefundEntitlementResult result = service.cancelForRefund(
                USER_ID, "old-refund", refundAt, refundAt);

        assertTrue(result.cancelledNow());
        assertTrue(result.hasActiveMembershipAfter());
        assertEquals(0, result.compactedFutureSegmentCount());
        assertEquals(refundAt.plusDays(MembershipPolicy.DURATION_DAYS), future.getStartTime());
        assertEquals(refundAt.plusDays(MembershipPolicy.DURATION_DAYS * 2L), future.getExpireTime());
        verify(userMembershipMapper, never()).rescheduleValidSegment(
                anyLong(), anyLong(), any(), any(), any());
    }

    @Test
    void 七月二日退款七月十日才通知_七月五日新购不误清且保留完整365天() {
        LocalDateTime refundAt = LocalDateTime.of(2026, 7, 2, 10, 0);
        LocalDateTime processedAt = LocalDateTime.of(2026, 7, 10, 10, 0);
        LocalDateTime newPurchasePaidAt = LocalDateTime.of(2026, 7, 5, 9, 30);
        UserMembership refunded = segment(
                1L, "old-member", refundAt.minusDays(20),
                LocalDateTime.of(2026, 8, 1, 10, 0), "0", 2000L);
        // 本地未收到退款通知时，7/5 新购被错误排到旧段到期后。
        UserMembership newPurchase = segment(
                2L, "new-member", refunded.getExpireTime(),
                refunded.getExpireTime().plusDays(MembershipPolicy.DURATION_DAYS), "0", 2000L);
        newPurchase.setPaidTime(newPurchasePaidAt);
        when(userMembershipMapper.selectByUserIdForUpdate(USER_ID))
                .thenReturn(List.of(refunded, newPurchase));
        when(userMembershipMapper.cancelForRefund(1L, "old-member", refundAt))
                .thenReturn(1);
        when(userMembershipMapper.rescheduleValidSegment(
                2L, USER_ID, newPurchasePaidAt,
                newPurchasePaidAt.plusDays(MembershipPolicy.DURATION_DAYS), refundAt))
                .thenReturn(1);

        MembershipRefundEntitlementResult result = service.cancelForRefund(
                USER_ID, "old-member", refundAt, processedAt);

        assertTrue(result.hasActiveMembershipAfter(),
                "清空账户必须按通知处理时刻判断，7/5 新购在 7/10 已生效");
        assertEquals(newPurchasePaidAt, newPurchase.getStartTime(),
                "新购段不得前移到真实支付之前");
        assertEquals(newPurchasePaidAt.plusDays(MembershipPolicy.DURATION_DAYS),
                newPurchase.getExpireTime(), "未退的支付必须保留完整365天");
        assertEquals(1, result.compactedFutureSegmentCount());
    }

    @Test
    void 参数不完整时拒绝且不获取任何锁() {
        ServiceException error = assertThrows(ServiceException.class,
                () -> service.cancelForRefund(
                        USER_ID, " ", LocalDateTime.now(), LocalDateTime.now()));

        assertEquals("退款会员订单信息不完整", error.getMessage());
        verify(beanAccountService, never()).lockAccount(anyLong());
        verify(userMembershipMapper, never()).selectByUserIdForUpdate(anyLong());
    }

    private UserMembership segment(long id,
                                   String sourceOrderNo,
                                   LocalDateTime startTime,
                                   LocalDateTime expireTime,
                                   String status,
                                   Long grantBeans) {
        UserMembership segment = new UserMembership();
        segment.setId(id);
        segment.setUserId(USER_ID);
        segment.setSourceOrderNo(sourceOrderNo);
        segment.setStartTime(startTime);
        segment.setExpireTime(expireTime);
        segment.setStatus(status);
        segment.setSnapshotGrantBeans(grantBeans);
        return segment;
    }

}
