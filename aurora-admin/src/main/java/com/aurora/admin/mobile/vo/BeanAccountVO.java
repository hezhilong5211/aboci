package com.aurora.admin.mobile.vo;

import com.aurora.admin.mobile.domain.BeanAccount;
import lombok.Data;

/**
 * 豆子账户出参(余额/累计获得/累计消耗/累计过期/累计退款回收),无账户视为全 0
 */
@Data
public class BeanAccountVO {

    /**
     * 当前余额
     */
    private Long balance;

    /**
     * 累计获得
     */
    private Long totalEarned;

    /**
     * 累计消耗
     */
    private Long totalConsumed;

    /**
     * 累计因会员赠豆到期失效
     */
    private Long totalExpired;

    /**
     * 累计因支付订单退款实际回收
     */
    private Long totalRevoked;

    /**
     * 由账户实体构造;account 为空则全 0
     */
    public static BeanAccountVO from(BeanAccount account) {
        BeanAccountVO vo = new BeanAccountVO();
        if (account == null) {
            vo.setBalance(0L);
            vo.setTotalEarned(0L);
            vo.setTotalConsumed(0L);
            vo.setTotalExpired(0L);
            vo.setTotalRevoked(0L);
        } else {
            vo.setBalance(account.getBalance());
            vo.setTotalEarned(account.getTotalEarned());
            vo.setTotalConsumed(account.getTotalConsumed());
            vo.setTotalExpired(account.getTotalExpired() == null ? 0L : account.getTotalExpired());
            vo.setTotalRevoked(account.getTotalRevoked() == null ? 0L : account.getTotalRevoked());
        }
        return vo;
    }
}
