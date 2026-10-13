package com.aurora.admin.mobile.service;

import com.aurora.admin.mobile.domain.BeanTransaction;
import com.aurora.admin.mobile.domain.UserMembership;
import com.aurora.admin.mobile.mapper.BeanTransactionMapper;
import com.aurora.admin.mobile.mapper.UserMembershipMapper;
import com.aurora.admin.mobile.service.impl.MembershipExpiryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会员到期结算单测：用确定时间验证 365 天边界、仅会员赠豆失效、幂等和重叠会员段。
 */
class MembershipExpiryServiceTest {

    private static final long USER_ID = 1001L;

    private UserMembershipMapper userMembershipMapper;
    private IBeanAccountService beanAccountService;
    private BeanTransactionMapper beanTransactionMapper;
    private MembershipExpiryServiceImpl service;

    @BeforeEach
    void setUp() {
        userMembershipMapper = mock(UserMembershipMapper.class);
        beanAccountService = mock(IBeanAccountService.class);
        beanTransactionMapper = mock(BeanTransactionMapper.class);
        TransactionTemplate transactionTemplate = immediateTransactionTemplate();
        service = new MembershipExpiryServiceImpl(
                userMembershipMapper, beanAccountService, beanTransactionMapper, transactionTemplate);
    }

    @Test
    void 会员未满365天不结算_到365天边界仅失效会员赠豆() {
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 12, 0);
        LocalDateTime expireTime = start.plusDays(MembershipPolicy.DURATION_DAYS);
        UserMembership segment = segment(1L, "0", expireTime);
        when(userMembershipMapper.selectByUserIdForUpdate(USER_ID)).thenReturn(List.of(segment));
        when(beanAccountService.expireMembershipGrantBalance(
                eq(USER_ID), eq(Long.MAX_VALUE), any(), any(), any())).thenReturn(2000L);
        when(userMembershipMapper.markExpirySettled(eq(1L), any())).thenReturn(1);

        long beforeExpiry = service.settleUserIfExpired(USER_ID, expireTime.minusNanos(1));

        assertEquals(0L, beforeExpiry);
        verify(userMembershipMapper, never()).markExpired(any(), any());
        verify(beanAccountService, never()).expireMembershipGrantBalance(
                any(), anyLong(), any(), any(), any());

        long atExpiry = service.settleUserIfExpired(USER_ID, expireTime);

        assertEquals(2000L, atExpiry);
        verify(userMembershipMapper).markExpired(USER_ID, expireTime);
        verify(beanAccountService).expireMembershipGrantBalance(
                eq(USER_ID), eq(Long.MAX_VALUE), any(), eq(null),
                eq("会员到期，仅失效剩余会员赠豆；加油包豆永久有效"));
    }

    @Test
    void 混合来源豆子到期仅失效会员赠豆_重复执行幂等() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 2, 1, 0, 0);
        UserMembership unsettled = segment(1L, "0", cutoff);
        UserMembership settled = segment(1L, "1", cutoff);
        settled.setExpirySettledTime(cutoff);
        when(userMembershipMapper.selectByUserIdForUpdate(USER_ID))
                .thenReturn(List.of(unsettled), List.of(settled));
        when(beanAccountService.expireMembershipGrantBalance(
                eq(USER_ID), eq(Long.MAX_VALUE), any(), any(), any())).thenReturn(1400L);
        when(userMembershipMapper.markExpirySettled(eq(1L), any())).thenReturn(1);

        long first = service.settleUserIfExpired(USER_ID, cutoff);
        long second = service.settleUserIfExpired(USER_ID, cutoff.plusMinutes(1));

        assertEquals(1400L, first);
        assertEquals(0L, second, "同一用户重复结算不得重复失效会员赠豆");
        verify(beanAccountService).expireMembershipGrantBalance(
                eq(USER_ID), eq(Long.MAX_VALUE), any(), eq(null),
                eq("会员到期，仅失效剩余会员赠豆；加油包豆永久有效"));
        verify(userMembershipMapper).markExpirySettled(1L, cutoff);
    }

    @Test
    void 存在另一有效会员段时只标记旧段_不清豆子() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 3, 1, 0, 0);
        UserMembership expired = segment(1L, "0", cutoff.minusSeconds(1));
        UserMembership active = segment(2L, "0", cutoff.plusDays(5));
        when(userMembershipMapper.selectByUserIdForUpdate(USER_ID))
                .thenReturn(List.of(expired, active));

        long cleared = service.settleUserIfExpired(USER_ID, cutoff);

        assertEquals(0L, cleared);
        verify(userMembershipMapper).markExpired(USER_ID, cutoff);
        verify(beanAccountService, never()).expireMembershipGrantBalance(
                any(), anyLong(), any(), any(), any());
    }

    @Test
    void 终止会员段已结算且无当前会员_仅晚到退款数量转为过期() {
        UserMembership settled = segment(1L, "1", LocalDateTime.of(2020, 1, 31, 0, 0));
        settled.setExpirySettledTime(LocalDateTime.of(2020, 1, 31, 0, 0));
        when(userMembershipMapper.selectByUserIdForUpdate(USER_ID)).thenReturn(List.of(settled));
        when(beanAccountService.expireMembershipGrantBalance(
                USER_ID, 50L, "refund-order-1", "membership-expire:1|refund-order-1", "晚到退款清零"))
                .thenReturn(50L);

        long cleared = service.expirePostExpiryRefund(
                USER_ID, 50L, "refund-order-1", "晚到退款清零");

        assertEquals(50L, cleared);
        verify(beanAccountService).expireMembershipGrantBalance(
                USER_ID, 50L, "refund-order-1", "membership-expire:1|refund-order-1", "晚到退款清零");
    }

    @Test
    void 当前仍有有效会员_晚到余额不清() {
        UserMembership settled = segment(1L, "1", LocalDateTime.of(2020, 1, 31, 0, 0));
        settled.setExpirySettledTime(LocalDateTime.of(2020, 1, 31, 0, 0));
        UserMembership active = segment(2L, "0", LocalDateTime.of(2099, 1, 1, 0, 0));
        active.setStartTime(LocalDateTime.of(2020, 2, 1, 0, 0));
        when(userMembershipMapper.selectByUserIdForUpdate(USER_ID))
                .thenReturn(List.of(settled, active));

        assertEquals(0L, service.expirePostExpiryRefund(
                USER_ID, 50L, "refund-order-2", "晚到退款清零"));

        verify(beanAccountService, never()).expireMembershipGrantBalance(
                any(), anyLong(), any(), any(), any());
    }

    @Test
    void 从未开通会员的用户_晚到余额不清() {
        when(userMembershipMapper.selectByUserIdForUpdate(USER_ID)).thenReturn(List.of());

        assertEquals(0L, service.expirePostExpiryRefund(
                USER_ID, 50L, "refund-order-3", "晚到退款清零"));

        verify(beanAccountService, never()).expireMembershipGrantBalance(
                any(), anyLong(), any(), any(), any());
    }

    @Test
    void 旧周期任务晚到退豆_当前已新订会员仍只清旧退豆() {
        LocalDateTime now = MembershipPolicy.now();
        UserMembership old = segment(1L, "1", now.minusDays(20));
        old.setExpirySettledTime(old.getExpireTime());
        UserMembership current = segment(2L, "0", now.plusDays(29));
        current.setStartTime(now.minusDays(1));
        when(userMembershipMapper.selectByUserIdForUpdate(USER_ID))
                .thenReturn(List.of(old, current));

        BeanTransaction deduction = new BeanTransaction();
        deduction.setUserId(USER_ID);
        deduction.setType("DEDUCT");
        deduction.setBizNo("old-task-1");
        deduction.setCreateTime(old.getStartTime().plusDays(1));
        when(beanTransactionMapper.selectLatestDeduct(USER_ID, "old-task-1"))
                .thenReturn(deduction);
        when(beanAccountService.expireMembershipGrantBalance(USER_ID, 50L,
                "post-expiry-refund:old-task-1", "membership-expire:1|old-task-1", "旧任务退豆清零"))
                .thenReturn(50L);

        long expired = service.expirePostExpiryRefund(USER_ID, 50L, "old-task-1",
                "post-expiry-refund:old-task-1", "旧任务退豆清零");

        assertEquals(50L, expired);
        verify(beanAccountService).expireMembershipGrantBalance(USER_ID, 50L,
                "post-expiry-refund:old-task-1", "membership-expire:1|old-task-1", "旧任务退豆清零");
    }

    @Test
    void 到期清零后证实是到期前续费_旧任务退豆不得误清() {
        LocalDateTime now = MembershipPolicy.now();
        UserMembership old = segment(1L, "1", now.minusDays(1));
        old.setExpirySettledTime(old.getExpireTime());
        UserMembership renewed = segment(2L, "0", now.plusDays(29));
        renewed.setStartTime(old.getExpireTime());
        when(userMembershipMapper.selectByUserIdForUpdate(USER_ID))
                .thenReturn(List.of(old, renewed));

        BeanTransaction deduction = new BeanTransaction();
        deduction.setUserId(USER_ID);
        deduction.setType("DEDUCT");
        deduction.setBizNo("continuous-task");
        deduction.setCreateTime(old.getStartTime().plusDays(1));
        when(beanTransactionMapper.selectLatestDeduct(USER_ID, "continuous-task"))
                .thenReturn(deduction);
        BeanTransaction restore = new BeanTransaction();
        restore.setType("RENEW_RESTORE");
        restore.setSourceBizNo("membership-expire:1");
        when(beanTransactionMapper.selectRenewalRestoreBySource(
                USER_ID, "membership-expire:1")).thenReturn(restore);

        assertEquals(0L, service.expirePostExpiryRefund(USER_ID, 50L, "continuous-task",
                "post-expiry-refund:continuous-task", "连续会员退豆"));

        verify(beanAccountService, never()).expireMembershipGrantBalance(
                any(), anyLong(), any(), any(), any());
    }

    @Test
    void 平台会员退款已回收权益_旧任务晚到退豆直接再回收() {
        LocalDateTime now = MembershipPolicy.now();
        UserMembership cancelled = segment(1L, "2", now.plusDays(10));
        when(userMembershipMapper.selectByUserIdForUpdate(USER_ID))
                .thenReturn(List.of(cancelled));
        when(beanAccountService.revokeCancelledTaskRefund(
                USER_ID, 50L, "cancelled-task", "post-expiry-refund:cancelled-task",
                "原支付权益已退款取消，晚到任务退豆同步回收"))
                .thenReturn(50L);

        long removed = service.expirePostExpiryRefund(
                USER_ID, 50L, "cancelled-task", "post-expiry-refund:cancelled-task", "晚到退豆");

        assertEquals(50L, removed);
        verify(beanAccountService, never()).expireMembershipGrantBalance(
                any(), anyLong(), any(), any(), any());
        verify(beanTransactionMapper, never()).selectLatestDeduct(any(), any());
    }

    @Test
    void 已取消批次只匹配部分退豆_无到期边界时返回已回收数不得丢失() {
        LocalDateTime now = MembershipPolicy.now();
        UserMembership cancelled = segment(1L, "2", now.plusDays(10));
        when(userMembershipMapper.selectByUserIdForUpdate(USER_ID))
                .thenReturn(List.of(cancelled));
        when(beanAccountService.revokeCancelledTaskRefund(
                USER_ID, 50L, "partial-cancelled-task", "post-expiry-refund:partial-cancelled-task",
                "原支付权益已退款取消，晚到任务退豆同步回收"))
                .thenReturn(30L);

        long removed = service.expirePostExpiryRefund(
                USER_ID, 50L, "partial-cancelled-task",
                "post-expiry-refund:partial-cancelled-task", "晚到退豆");

        assertEquals(30L, removed);
        verify(beanAccountService, never()).expireMembershipGrantBalance(
                any(), anyLong(), any(), any(), any());
    }

    private UserMembership segment(Long id, String status, LocalDateTime expireTime) {
        UserMembership segment = new UserMembership();
        segment.setId(id);
        segment.setUserId(USER_ID);
        segment.setStatus(status);
        segment.setStartTime(expireTime.minusDays(MembershipPolicy.DURATION_DAYS));
        segment.setExpireTime(expireTime);
        return segment;
    }

    @SuppressWarnings("unchecked")
    private TransactionTemplate immediateTransactionTemplate() {
        TransactionTemplate template = mock(TransactionTemplate.class);
        when(template.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<Object> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        return template;
    }
}
