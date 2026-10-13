package com.aurora.admin.mobile.service;

import java.time.LocalDateTime;

/** 会员到期结算：标记过期段，并在最后一个有效段结束时失效剩余会员赠豆。 */
public interface IMembershipExpiryService {

    /** 以当前时间结算单个用户；返回本次失效的会员赠豆数。 */
    long settleUserIfExpired(Long userId);

    /** 以指定截止时间结算单个用户，供确定性测试与批处理复用。 */
    long settleUserIfExpired(Long userId, LocalDateTime cutoff);

    /** 扫描并结算一批到期用户；返回成功处理用户数。 */
    int settleExpiredBatch(LocalDateTime cutoff, int batchSize);

    /**
     * 会员周期已完成到期结算后，仅将晚到退款对应数量转为过期豆；
     * 从未有会员或当前仍有有效会员时不处理。
     */
    long expirePostExpiryRefund(Long userId, long amount, String bizNo, String remark);

    /**
     * 根据原始任务扣豆流水判定退豆属于哪个会员周期。
     * 旧周期已真正终止时，即使用户后来又开通了新会员，也只失效这笔旧任务退回的会员豆。
     */
    long expirePostExpiryRefund(Long userId, long amount, String taskBizNo,
                                String bizNo, String remark);
}
