package com.aurora.admin.mobile.billing;

import com.aurora.admin.mobile.service.IBeanAccountService;
import com.aurora.admin.mobile.service.IMembershipExpiryService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** 验证 AI 退豆后会员到期补偿的调用顺序。 */
class BeanDeductAdapterTest {

    @Test
    void cappedSettlementFirstExpiresMembershipAndReturnsTheAtomicDebit() {
        IBeanAccountService accounts = mock(IBeanAccountService.class);
        IMembershipExpiryService expiry = mock(IMembershipExpiryService.class);
        BeanDeductAdapter adapter = new BeanDeductAdapter(accounts, expiry);
        when(accounts.deductUpToBalance(1001L, 80L, "text:9", "text")).thenReturn(20L);
        assertEquals(20L, adapter.deductUpToBalance(1001L, 80L, "text:9", "text"));
        InOrder order = inOrder(expiry, accounts);
        order.verify(expiry).settleUserIfExpired(1001L);
        order.verify(accounts).deductUpToBalance(1001L, 80L, "text:9", "text");
    }

    @Test
    void refundFirstCreditsThenSettlesAndClearsPostExpiryBalance() {
        IBeanAccountService beanAccountService = mock(IBeanAccountService.class);
        IMembershipExpiryService membershipExpiryService = mock(IMembershipExpiryService.class);
        BeanDeductAdapter adapter = new BeanDeductAdapter(beanAccountService, membershipExpiryService);

        adapter.refund(1001L, 50L, "task-9", "生成失败退豆");

        InOrder order = inOrder(beanAccountService, membershipExpiryService);
        order.verify(beanAccountService).refund(1001L, 50L, "task-9", "生成失败退豆");
        order.verify(membershipExpiryService).settleUserIfExpired(1001L);
        order.verify(membershipExpiryService).expirePostExpiryRefund(
                1001L, 50L, "task-9", "post-expiry-refund:task-9",
                "会员已到期，晚到退款豆同步清零");
    }
}
