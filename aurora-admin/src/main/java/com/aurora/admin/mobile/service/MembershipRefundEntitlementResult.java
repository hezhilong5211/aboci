package com.aurora.admin.mobile.service;

/**
 * 会员退款权益取消结果。
 *
 * @param targetFound                 是否找到 source_order_no 对应的会员段
 * @param cancelledNow                本次是否首次将目标段改为取消
 * @param cancelledMembershipId       目标会员段 ID，未找到时为 null
 * @param cancelledGrantBeans         目标会员段的赠送豆快照数
 * @param hasActiveMembershipAfter    取消并压缩后，处理时刻是否仍有实际有效会员段
 * @param compactedFutureSegmentCount 本次被前移压缩的未生效续费段数
 */
public record MembershipRefundEntitlementResult(
        boolean targetFound,
        boolean cancelledNow,
        Long cancelledMembershipId,
        long cancelledGrantBeans,
        boolean hasActiveMembershipAfter,
        int compactedFutureSegmentCount) {

    public static MembershipRefundEntitlementResult targetMissing(boolean hasActiveMembership) {
        return new MembershipRefundEntitlementResult(
                false, false, null, 0L, hasActiveMembership, 0);
    }
}
