package com.aurora.admin.mobile.service.impl;

import com.aurora.admin.mobile.domain.UserMembership;
import com.aurora.admin.mobile.mapper.UserMembershipMapper;
import com.aurora.admin.mobile.service.IBeanAccountService;
import com.aurora.admin.mobile.service.IMembershipRefundEntitlementService;
import com.aurora.admin.mobile.service.MembershipRefundEntitlementResult;
import com.aurora.common.core.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 会员退款权益处理实现。
 */
@Service
@RequiredArgsConstructor
public class MembershipRefundEntitlementServiceImpl
        implements IMembershipRefundEntitlementService {

    private static final String STATUS_VALID = "0";
    private static final String STATUS_CANCELLED = "2";

    private final IBeanAccountService beanAccountService;
    private final UserMembershipMapper userMembershipMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MembershipRefundEntitlementResult cancelForRefund(Long userId,
                                                               String sourceOrderNo,
                                                               LocalDateTime refundAt,
                                                               LocalDateTime processedAt) {
        validateArguments(userId, sourceOrderNo, refundAt, processedAt);

        // 调用方已锁 pay_order；本服务继续按统一顺序锁账户和会员段。
        beanAccountService.lockAccount(userId);
        List<UserMembership> lockedSegments = userMembershipMapper.selectByUserIdForUpdate(userId);
        if (lockedSegments == null) {
            lockedSegments = List.of();
        }

        UserMembership target = lockedSegments.stream()
                .filter(segment -> sourceOrderNo.equals(segment.getSourceOrderNo()))
                .findFirst()
                .orElse(null);
        if (target == null) {
            return MembershipRefundEntitlementResult.targetMissing(
                    hasActiveMembership(lockedSegments, processedAt));
        }

        boolean cancelledNow = false;
        if (!STATUS_CANCELLED.equals(target.getStatus())) {
            int updated = userMembershipMapper.cancelForRefund(
                    target.getId(), sourceOrderNo, refundAt);
            if (updated != 1) {
                throw new ServiceException("会员退款权益取消失败");
            }
            target.setStatus(STATUS_CANCELLED);
            target.setUpdateTime(refundAt);
            cancelledNow = true;
        }

        // 目标在退款时刻已经到期时，取消它不会给当前/未来链新增空档，
        // 不得倒推或改动后来新购的会员权益。
        boolean targetAffectsRemainingChain = target.getExpireTime() != null
                && target.getExpireTime().isAfter(refundAt);
        int compactedCount = targetAffectsRemainingChain
                ? compactFutureRenewalChain(userId, lockedSegments, refundAt)
                : 0;
        // 退款消息可能延迟数天才到达：是否清空当前账户必须看“现在”，
        // 而不能回到历史退款时刻，否则会误清通知延迟期间新购会员的豆子。
        boolean activeAfter = hasActiveMembership(lockedSegments, processedAt);
        long grantBeans = target.getSnapshotGrantBeans() == null
                ? 0L : Math.max(0L, target.getSnapshotGrantBeans());

        return new MembershipRefundEntitlementResult(
                true,
                cancelledNow,
                target.getId(),
                grantBeans,
                activeAfter,
                compactedCount);
    }

    /**
     * 只消除续费链中的空档：未来段在已有覆盖期内重叠时不后移，
     * 避免改变用户已有权益；只把空档后的段前移到当前覆盖终点。
     */
    private int compactFutureRenewalChain(Long userId,
                                          List<UserMembership> lockedSegments,
                                          LocalDateTime refundAt) {
        LocalDateTime coverageEnd = lockedSegments.stream()
                .filter(segment -> isActiveAt(segment, refundAt))
                .map(UserMembership::getExpireTime)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(refundAt);

        List<UserMembership> futureSegments = new ArrayList<>();
        for (UserMembership segment : lockedSegments) {
            if (STATUS_VALID.equals(segment.getStatus())
                    && segment.getStartTime() != null
                    && segment.getStartTime().isAfter(refundAt)
                    && segment.getExpireTime() != null) {
                futureSegments.add(segment);
            }
        }
        futureSegments.sort(Comparator
                .comparing(UserMembership::getStartTime)
                .thenComparing(UserMembership::getId,
                        Comparator.nullsLast(Comparator.naturalOrder())));

        int compactedCount = 0;
        for (UserMembership segment : futureSegments) {
            LocalDateTime paymentFloor = segment.getPaidTime();
            // V34 前无法回填真实支付时间的历史段宁可保留原起点，绝不猜测并前移权益。
            if (paymentFloor == null) {
                paymentFloor = segment.getStartTime();
            }
            LocalDateTime newStart = coverageEnd.isAfter(paymentFloor)
                    ? coverageEnd : paymentFloor;
            boolean hasGap = segment.getStartTime().isAfter(coverageEnd);
            boolean violatesPaymentFloor = segment.getStartTime().isBefore(paymentFloor);
            if ((hasGap || violatesPaymentFloor)
                    && !segment.getStartTime().equals(newStart)) {
                Duration originalDuration = Duration.between(
                        segment.getStartTime(), segment.getExpireTime());
                if (originalDuration.isZero() || originalDuration.isNegative()) {
                    throw new ServiceException("会员续期段时长异常");
                }
                LocalDateTime newExpire = newStart.plus(originalDuration);
                int updated = userMembershipMapper.rescheduleValidSegment(
                        segment.getId(), userId, newStart, newExpire, refundAt);
                if (updated != 1) {
                    throw new ServiceException("会员退款后续费链压缩失败");
                }
                segment.setStartTime(newStart);
                segment.setExpireTime(newExpire);
                segment.setUpdateTime(refundAt);
                compactedCount++;
            }
            if (segment.getExpireTime().isAfter(coverageEnd)) {
                coverageEnd = segment.getExpireTime();
            }
        }
        return compactedCount;
    }

    private boolean hasActiveMembership(List<UserMembership> segments,
                                        LocalDateTime refundAt) {
        return segments.stream().anyMatch(segment -> isActiveAt(segment, refundAt));
    }

    /** 精确的左闭右开边界：start <= refundAt < expire。 */
    private boolean isActiveAt(UserMembership segment, LocalDateTime refundAt) {
        return STATUS_VALID.equals(segment.getStatus())
                && segment.getStartTime() != null
                && !segment.getStartTime().isAfter(refundAt)
                && segment.getExpireTime() != null
                && segment.getExpireTime().isAfter(refundAt);
    }

    private void validateArguments(Long userId,
                                   String sourceOrderNo,
                                   LocalDateTime refundAt,
                                   LocalDateTime processedAt) {
        if (userId == null || sourceOrderNo == null || sourceOrderNo.isBlank()
                || refundAt == null || processedAt == null) {
            throw new ServiceException("退款会员订单信息不完整");
        }
    }
}
