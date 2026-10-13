package com.aurora.admin.mobile.service;

import java.time.LocalDateTime;

/**
 * 会员支付订单退款时的会员权益处理。
 *
 * <p>锁顺序固定为 pay_order（调用方）→ bean_account → user_membership，
 * 以与支付履约和到期结算保持一致。</p>
 */
public interface IMembershipRefundEntitlementService {

    /**
     * 精确取消指定支付订单对应的会员段，并压缩取消后的未生效续费链。
     *
     * @param userId        用户 ID
     * @param sourceOrderNo 会员支付订单号（user_membership.source_order_no）
     * @param refundAt      微信侧退款成功时刻，仅用于修正退款造成的会员链时间
     * @param processedAt   本次通知/查单的实际处理时刻，用于判断当前是否仍有会员权益
     * @return 取消结果；找不到目标段时安全返回 targetFound=false
     */
    MembershipRefundEntitlementResult cancelForRefund(Long userId,
                                                        String sourceOrderNo,
                                                        LocalDateTime refundAt,
                                                        LocalDateTime processedAt);
}
