package com.aurora.admin.mobile.service.impl;

import com.aurora.admin.mobile.domain.BeanTransaction;
import com.aurora.admin.mobile.domain.UserMembership;
import com.aurora.admin.mobile.mapper.BeanTransactionMapper;
import com.aurora.admin.mobile.mapper.UserMembershipMapper;
import com.aurora.admin.mobile.service.IBeanAccountService;
import com.aurora.admin.mobile.service.IMembershipExpiryService;
import com.aurora.admin.mobile.service.MembershipPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * 会员到期结算实现。
 *
 * <p>每个用户统一锁序：bean_account FOR UPDATE → user_membership FOR UPDATE → 标记过期 →
 * 最后有效段结束时清空余额并写 EXPIRE 流水。TransactionTemplate 保证无论来自定时任务还是接口惰性
 * 结算，都在真实事务中执行且不会受同类自调用影响。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MembershipExpiryServiceImpl implements IMembershipExpiryService {

    private static final String STATUS_VALID = "0";
    private static final String STATUS_EXPIRED = "1";

    private final UserMembershipMapper userMembershipMapper;
    private final IBeanAccountService beanAccountService;
    private final BeanTransactionMapper beanTransactionMapper;
    private final TransactionTemplate transactionTemplate;

    @Override
    public long settleUserIfExpired(Long userId) {
        return settleUserIfExpired(userId, MembershipPolicy.now());
    }

    @Override
    public long settleUserIfExpired(Long userId, LocalDateTime cutoff) {
        if (userId == null || cutoff == null) {
            return 0L;
        }
        Long result = transactionTemplate.execute(status -> settleLocked(userId, cutoff));
        return result == null ? 0L : result;
    }

    @Override
    public int settleExpiredBatch(LocalDateTime cutoff, int batchSize) {
        LocalDateTime effectiveCutoff = cutoff == null ? MembershipPolicy.now() : cutoff;
        int limit = Math.max(1, Math.min(batchSize, 2000));
        List<Long> userIds = userMembershipMapper.selectExpiryCandidateUserIds(effectiveCutoff, limit);
        int processed = 0;
        for (Long userId : userIds) {
            try {
                settleUserIfExpired(userId, effectiveCutoff);
                processed++;
            } catch (Exception e) {
                // 单用户失败不影响其他用户，下一轮继续补偿。
                log.error("会员到期结算失败，保留待下次重试: userId={}", userId, e);
            }
        }
        return processed;
    }

    @Override
    public long expirePostExpiryRefund(Long userId, long amount, String bizNo, String remark) {
        return expirePostExpiryRefund(userId, amount, null, bizNo, remark);
    }

    @Override
    public long expirePostExpiryRefund(Long userId, long amount, String taskBizNo,
                                       String bizNo, String remark) {
        if (userId == null || amount <= 0) {
            return 0L;
        }
        Long result = transactionTemplate.execute(status -> {
            beanAccountService.lockAccount(userId);
            List<UserMembership> segments = userMembershipMapper.selectByUserIdForUpdate(userId);
            if (segments == null || segments.isEmpty()) {
                return 0L;
            }
            long revoked = beanAccountService.revokeCancelledTaskRefund(
                    userId, amount, taskBizNo, bizNo,
                    "原支付权益已退款取消，晚到任务退豆同步回收");
            long remaining = amount - revoked;
            if (remaining <= 0) {
                return revoked;
            }
            LocalDateTime now = MembershipPolicy.now();
            boolean hasActive = segments.stream()
                    .anyMatch(segment -> STATUS_VALID.equals(segment.getStatus())
                            && segment.getStartTime() != null
                            && !segment.getStartTime().isAfter(now)
                            && segment.getExpireTime() != null
                            && segment.getExpireTime().isAfter(now));

            UserMembership terminal = null;
            if (taskBizNo != null && !taskBizNo.isBlank()) {
                BeanTransaction deduction = beanTransactionMapper.selectLatestDeduct(userId, taskBizNo);
                if (deduction != null && deduction.getCreateTime() != null) {
                    terminal = firstUnrestoredTerminalAfterTask(
                            userId, segments, deduction.getCreateTime());
                    // 能定位原扣豆时刻时，必须以原周期为准，不能被“当前又有新会员”覆盖。
                    if (terminal == null) {
                        return revoked;
                    }
                }
            }

            // 存量无任务流水时保留原有保守逻辑：当前无会员且确已完成终止结算才清。
            if (terminal == null) {
                if (hasActive) {
                    return revoked;
                }
                terminal = terminalExpiredSegment(segments, now);
            }
            if (terminal == null || terminal.getExpirySettledTime() == null) {
                return revoked;
            }
            String expirySource = "membership-expire:" + terminal.getId();
            // uk_type_source_biz 对每种流水的 source_biz_no 唯一；同一会员边界可有多个
            // 晚到任务退豆，因此用“周期|任务”保证每笔 EXPIRE 唯一，重放时再归一到周期。
            String ledgerSource = expirySource + "|"
                    + (taskBizNo == null || taskBizNo.isBlank() ? bizNo : taskBizNo);
            long expired = beanAccountService.expireMembershipGrantBalance(
                    userId, remaining, bizNo, ledgerSource, remark);
            return revoked + expired;
        });
        return result == null ? 0L : result;
    }

    /**
     * 找原任务所在连续会员链之后第一个“真正终止”边界。
     * <p>
     * expiry_settled_time 说明当时确实做过终止结算；若后续出现指向该边界的
     * RENEW_RESTORE，说明支付其实发生在到期前，该边界已被证实为连续续费，继续查找后续边界。
     */
    private UserMembership firstUnrestoredTerminalAfterTask(Long userId,
                                                             List<UserMembership> segments,
                                                             LocalDateTime deductedAt) {
        boolean deductedInsideMembership = segments.stream()
                .anyMatch(segment -> segment.getStartTime() != null
                        && !segment.getStartTime().isAfter(deductedAt)
                        && segment.getExpireTime() != null
                        && segment.getExpireTime().isAfter(deductedAt));
        if (!deductedInsideMembership) {
            return null;
        }
        return segments.stream()
                .filter(segment -> segment.getId() != null
                        && segment.getExpirySettledTime() != null
                        && segment.getExpireTime() != null
                        && segment.getExpireTime().isAfter(deductedAt))
                .sorted(Comparator.comparing(UserMembership::getExpireTime)
                        .thenComparing(UserMembership::getId))
                .filter(segment -> beanTransactionMapper.selectRenewalRestoreBySource(
                        userId, "membership-expire:" + segment.getId()) == null)
                .findFirst()
                .orElse(null);
    }

    private long settleLocked(Long userId, LocalDateTime cutoff) {
        // bean_account 是每用户统一互斥锁；所有会员开通路径也遵循同一锁序。
        beanAccountService.lockAccount(userId);
        List<UserMembership> segments = userMembershipMapper.selectByUserIdForUpdate(userId);
        if (segments == null || segments.isEmpty()) {
            return 0L;
        }

        boolean reachedAnyExpiry = segments.stream()
                .anyMatch(segment -> isExpirable(segment)
                        && segment.getExpireTime() != null
                        && !segment.getExpireTime().isAfter(cutoff));
        if (!reachedAnyExpiry) {
            return 0L;
        }

        userMembershipMapper.markExpired(userId, cutoff);
        boolean hasAnotherActiveSegment = segments.stream()
                .anyMatch(segment -> STATUS_VALID.equals(segment.getStatus())
                        && segment.getStartTime() != null
                        && !segment.getStartTime().isAfter(cutoff)
                        && segment.getExpireTime() != null
                        && segment.getExpireTime().isAfter(cutoff));
        if (hasAnotherActiveSegment) {
            return 0L;
        }

        UserMembership terminal = terminalExpiredSegment(segments, cutoff);
        if (terminal == null) {
            return 0L;
        }
        if (terminal.getExpirySettledTime() != null) {
            return 0L;
        }
        long cleared = beanAccountService.expireMembershipGrantBalance(userId, Long.MAX_VALUE,
                "membership-expire:" + terminal.getId(),
                null, "会员到期，仅失效剩余会员赠豆；加油包豆永久有效");
        int marked = userMembershipMapper.markExpirySettled(terminal.getId(), cutoff);
        if (marked != 1) {
            throw new IllegalStateException("会员到期结算标记写入失败: membershipId=" + terminal.getId());
        }
        if (cleared > 0) {
            log.info("会员赠豆到期已失效: userId={}, amount={}, expireTime={}",
                    userId, cleared, terminal.getExpireTime());
        }
        return cleared;
    }

    private boolean isExpirable(UserMembership segment) {
        return segment != null && (STATUS_VALID.equals(segment.getStatus())
                || STATUS_EXPIRED.equals(segment.getStatus()));
    }

    private UserMembership terminalExpiredSegment(List<UserMembership> segments, LocalDateTime cutoff) {
        return segments.stream()
                .filter(segment -> isExpirable(segment)
                        && segment.getExpireTime() != null
                        && !segment.getExpireTime().isAfter(cutoff))
                .max(Comparator.comparing(UserMembership::getExpireTime)
                        .thenComparing(UserMembership::getId, Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
    }
}
