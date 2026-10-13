package com.aurora.admin.mobile.billing;

import com.aurora.admin.mobile.service.IBeanAccountService;
import com.aurora.admin.mobile.service.IMembershipExpiryService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

/** 验证 AI 退豆后会员到期补偿的调用顺序。 */
class BeanDeductAdapterTest {

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
