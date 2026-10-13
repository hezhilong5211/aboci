package com.aurora.admin.mobile.service;

/**
 * 支付订单退款时的豆子权益回收结果。
 *
 * @param requested       本次应回收数量
 * @param revoked         实际从余额回收的数量
 * @param shortfall       因豆子已被使用等原因无法回收的数量
 * @param balanceAfter    回收后余额
 * @param alreadyProcessed 是否为同一订单退款的幂等重放
 */
public record BeanRefundRevokeResult(long requested,
                                     long revoked,
                                     long shortfall,
                                     long balanceAfter,
                                     boolean alreadyProcessed) {
}
