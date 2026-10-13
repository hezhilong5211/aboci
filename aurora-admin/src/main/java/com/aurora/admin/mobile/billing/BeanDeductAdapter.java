package com.aurora.admin.mobile.billing;

import com.aurora.admin.mobile.service.IBeanAccountService;
import com.aurora.admin.mobile.service.IMembershipExpiryService;
import com.aurora.admin.mobile.vo.BeanAccountVO;
import com.aurora.ai.agent.billing.IBeanDeductPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 豆子扣减端口适配器
 *
 * 实现 aurora-ai 定义的 SPI 接口 {@link IBeanDeductPort},内部转调 {@link IBeanAccountService}
 * (豆子余额唯一写入路径)。依赖方向 admin→ai,规避 ai→admin 循环依赖。
 *
 * @author aurora
 */
@Component
@RequiredArgsConstructor
public class BeanDeductAdapter implements IBeanDeductPort {

    private final IBeanAccountService beanAccountService;
    private final IMembershipExpiryService membershipExpiryService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deduct(Long userId, long beans, String bizNo, String remark) {
        membershipExpiryService.settleUserIfExpired(userId);
        beanAccountService.deduct(userId, beans, bizNo, remark);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refund(Long userId, long beans, String bizNo, String remark) {
        beanAccountService.refund(userId, beans, bizNo, remark);
        // 先处理尚未结算的到期事件；再覆盖“到期已结算后才晚到退款”的场景。
        membershipExpiryService.settleUserIfExpired(userId);
        membershipExpiryService.expirePostExpiryRefund(userId, beans, bizNo,
                "post-expiry-refund:" + (bizNo == null ? "unknown" : bizNo),
                "会员已到期，晚到退款豆同步清零");
    }

    @Override
    public long getBalance(Long userId) {
        membershipExpiryService.settleUserIfExpired(userId);
        BeanAccountVO account = beanAccountService.getAccount(userId);
        if (account == null || account.getBalance() == null) {
            return 0L;
        }
        return account.getBalance();
    }
}
