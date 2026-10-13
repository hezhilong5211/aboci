package com.aurora.admin.mobile.service;

/**
 * 续费支付时刻位于旧会员到期前，但回调晚于到期时的豆子恢复结果。
 *
 * @param restored          本次恢复的豆子数
 * @param balanceAfter      恢复后余额
 * @param alreadyProcessed  该续费订单或对应到期清零是否已处理
 * @param restoredByOrderNo 实际执行恢复的续费订单号（无需恢复时为 null）
 */
public record BeanRenewalRestoreResult(long restored,
                                       long balanceAfter,
                                       boolean alreadyProcessed,
                                       String restoredByOrderNo) {
}
