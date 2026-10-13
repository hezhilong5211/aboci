package com.aurora.admin.mobile.service.impl;

import com.aurora.admin.mobile.domain.BeanAccount;
import com.aurora.admin.mobile.domain.BeanTransaction;
import com.aurora.admin.mobile.mapper.BeanAccountMapper;
import com.aurora.admin.mobile.mapper.BeanTransactionMapper;
import com.aurora.admin.mobile.service.BeanRefundRevokeResult;
import com.aurora.admin.mobile.service.BeanRenewalRestoreResult;
import com.aurora.admin.mobile.service.IBeanAccountService;
import com.aurora.admin.mobile.vo.BeanAccountVO;
import com.aurora.common.core.exception.ServiceException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 豆子账户服务实现(豆子余额唯一写入路径)
 * 余额变动一律走 Mapper 的原子 UPDATE,扣减用 WHERE balance>=amount 保证并发不超扣;
 * 每次变动同事务追加一条流水(含变动后余额快照),全部写方法 @Transactional(rollbackFor=Exception)。
 */
@Service
@RequiredArgsConstructor
public class BeanAccountServiceImpl extends ServiceImpl<BeanAccountMapper, BeanAccount>
        implements IBeanAccountService {

    private final BeanTransactionMapper beanTransactionMapper;

    /** 流水类型 */
    private static final String TYPE_GIFT = "GIFT";
    private static final String TYPE_ISSUE = "ISSUE";
    private static final String TYPE_RECHARGE = "RECHARGE";
    private static final String TYPE_DEDUCT = "DEDUCT";
    private static final String TYPE_REFUND = "REFUND";
    private static final String TYPE_ADJUST = "ADJUST";
    private static final String TYPE_EXPIRE = "EXPIRE";
    private static final String TYPE_REVOKE = "REVOKE";
    private static final String TYPE_RENEW_RESTORE = "RENEW_RESTORE";
    private static final String REVOKE_TARGET_PREFIX = "TARGET:";
    private static final String REVOKE_ALL_PREFIX = "ALL:";
    private static final String REVOKE_LATE_TASK_PREFIX = "LATE_TASK:";
    private static final int MAX_REMARK_LENGTH = 255;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void ensureAccount(Long userId) {
        if (findByUserId(userId) != null) {
            return;
        }
        BeanAccount account = new BeanAccount();
        account.setUserId(userId);
        account.setBalance(0L);
        account.setTotalEarned(0L);
        account.setTotalConsumed(0L);
        account.setTotalExpired(0L);
        account.setTotalRevoked(0L);
        account.setCreateTime(LocalDateTime.now());
        account.setUpdateTime(LocalDateTime.now());
        try {
            baseMapper.insert(account);
        } catch (DuplicateKeyException e) {
            // 并发下另一线程已建账户(user_id 唯一索引兜底),忽略即可
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BeanAccount lockAccount(Long userId) {
        ensureAccount(userId);
        BeanAccount account = baseMapper.selectByUserIdForUpdate(userId);
        if (account == null) {
            throw new ServiceException("豆子账户不存在");
        }
        return account;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void grant(Long userId, long amount, String bizNo, String remark) {
        increase(userId, amount, TYPE_GIFT, bizNo, remark);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void issue(Long userId, long amount, String bizNo, String remark) {
        increase(userId, amount, TYPE_ISSUE, bizNo, remark);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recharge(Long userId, long amount, String bizNo, String remark) {
        increase(userId, amount, TYPE_RECHARGE, bizNo, remark);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deduct(Long userId, long amount, String bizNo, String remark) {
        deductInternal(userId, amount, TYPE_DEDUCT, bizNo, remark);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long deductUpToBalance(Long userId, long amount, String bizNo, String remark) {
        requirePositive(amount);
        if (bizNo == null || bizNo.isBlank()) throw new ServiceException("结算业务单号不能为空");
        BeanAccount account = lockAccount(userId);
        BeanTransaction existing = beanTransactionMapper.selectLatestDeduct(userId, bizNo);
        if (existing != null) return Math.max(0L, -existing.getChangeValue());
        long actual = Math.min(amount, safeBalance(account));
        if (actual > 0 && baseMapper.deductIfEnough(userId, actual) != 1) {
            throw new ServiceException("对话扣豆结算失败");
        }
        long balanceAfter = actual == 0 ? safeBalance(account) : safeBalance(findByUserId(userId));
        // A zero debit also closes this settlement, so a retry after recharge cannot charge it again.
        writeTransaction(userId, TYPE_DEDUCT, -actual, balanceAfter, bizNo, remark);
        return actual;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refund(Long userId, long amount, String bizNo, String remark) {
        requirePositive(amount);
        ensureAccount(userId);
        if (bizNo != null && !bizNo.isBlank()) {
            lockAccount(userId);
            BeanTransaction existing = beanTransactionMapper.selectLatestRefund(userId, bizNo);
            if (existing != null) {
                if (!Long.valueOf(amount).equals(existing.getChangeValue())) {
                    throw new ServiceException("业务单号重复且退豆数量不一致");
                }
                return;
            }
        }
        baseMapper.refund(userId, amount);
        BeanAccount account = findByUserId(userId);
        writeTransaction(userId, TYPE_REFUND, amount, account.getBalance(), bizNo, remark);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adjust(Long userId, long delta, String remark) {
        if (delta == 0) {
            throw new ServiceException("调整数量不能为 0");
        }
        if (delta > 0) {
            increase(userId, delta, TYPE_ADJUST, null, remark);
        } else {
            deductInternal(userId, -delta, TYPE_ADJUST, null, remark);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long clearForMembershipExpiry(Long userId, String bizNo, String remark) {
        BeanAccount account = lockAccount(userId);
        long expired = account.getBalance() == null ? 0L : account.getBalance();
        if (expired <= 0) {
            return 0L;
        }
        int rows = baseMapper.clearForMembershipExpiry(userId);
        if (rows != 1) {
            throw new ServiceException("会员到期豆子清零失败");
        }
        writeTransaction(userId, TYPE_EXPIRE, -expired, 0L, bizNo, remark);
        return expired;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long expireMembershipGrantBalance(Long userId, long maxAmount, String bizNo,
                                             String sourceBizNo, String remark) {
        if (userId == null || maxAmount <= 0) {
            return 0L;
        }
        BeanAccount account = lockAccount(userId);
        long balance = safeBalance(account);
        if (balance <= 0) {
            return 0L;
        }
        long membershipRemaining = replayCreditLots(userId, null).stream()
                .filter(CreditLot::membershipGrant)
                .filter(lot -> !lot.revoked())
                .mapToLong(CreditLot::remaining)
                .sum();
        long expired = Math.min(balance, Math.min(maxAmount, membershipRemaining));
        if (expired <= 0) {
            return 0L;
        }
        int rows = baseMapper.expireAmount(userId, expired);
        if (rows != 1) {
            throw new ServiceException("会员赠豆到期结算失败");
        }
        long balanceAfter = safeBalance(findByUserId(userId));
        writeTransaction(userId, TYPE_EXPIRE, -expired, balanceAfter,
                bizNo, sourceBizNo, remark);
        return expired;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long expireAmount(Long userId, long amount, String bizNo, String remark) {
        return expireAmount(userId, amount, bizNo, null, remark);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long expireAmount(Long userId, long amount, String bizNo,
                             String sourceBizNo, String remark) {
        requirePositive(amount);
        lockAccount(userId);
        int rows = baseMapper.expireAmount(userId, amount);
        if (rows != 1) {
            return 0L;
        }
        BeanAccount account = findByUserId(userId);
        writeTransaction(userId, TYPE_EXPIRE, -amount, account.getBalance(),
                bizNo, sourceBizNo, remark);
        return amount;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long revokeCancelledTaskRefund(Long userId, long amount, String taskBizNo,
                                          String bizNo, String remark) {
        if (userId == null || amount <= 0 || taskBizNo == null || taskBizNo.isBlank()) {
            return 0L;
        }
        BeanAccount account = lockAccount(userId);
        long invalid = cancelledTaskRefundAmount(userId, taskBizNo, amount);
        long revoked = Math.min(Math.min(amount, invalid), safeBalance(account));
        if (revoked <= 0) {
            return 0L;
        }
        int rows = baseMapper.revokeForOrderRefund(userId, revoked);
        if (rows != 1) {
            throw new ServiceException("已取消权益的晚到任务退豆回收失败");
        }
        long balanceAfter = safeBalance(findByUserId(userId));
        writeTransaction(userId, TYPE_REVOKE, -revoked, balanceAfter, bizNo,
                REVOKE_LATE_TASK_PREFIX + taskBizNo,
                auditRemark(remark, "已取消批次晚到退豆回收=" + revoked));
        return revoked;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BeanRefundRevokeResult revokeForOrderRefund(Long userId, long requested,
                                                       String bizNo, String remark) {
        if (requested < 0) {
            throw new ServiceException("退款权益回收数量不能小于 0");
        }
        return revokeForOrderRefundInternal(userId, requested, false, bizNo, remark);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BeanRefundRevokeResult revokeAllForOrderRefund(Long userId, String bizNo, String remark) {
        return revokeForOrderRefundInternal(userId, 0L, true, bizNo, remark);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BeanRenewalRestoreResult restoreExpiredForRenewal(Long userId, String expiryBizNo,
                                                             String renewalOrderNo, String remark) {
        requireUserAndBizNo(userId, renewalOrderNo);
        if (expiryBizNo == null || expiryBizNo.isBlank()) {
            throw new ServiceException("旧会员到期结算单号不能为空");
        }

        BeanAccount locked = lockAccount(userId);
        BeanTransaction byOrder = beanTransactionMapper.selectRenewalRestoreByOrder(userId, renewalOrderNo);
        if (byOrder != null) {
            return restoreResult(byOrder, true);
        }

        // 多笔续费在旧会员到期前同时付款时，同一笔 EXPIRE 只能恢复一次。
        BeanTransaction bySource = beanTransactionMapper.selectRenewalRestoreBySource(userId, expiryBizNo);
        if (bySource != null) {
            return restoreResult(bySource, true);
        }

        Long restorable = beanTransactionMapper.selectExpiredAmountForRenewal(userId, expiryBizNo);
        if (restorable == null || restorable <= 0) {
            // 到期时余额为 0 不会写 EXPIRE 流水，此时无需恢复。
            return new BeanRenewalRestoreResult(0L, safeBalance(locked), false, null);
        }

        // 除原到期清零外，连续续费回调延迟期间可能还有“旧任务晚到退豆”被再次过期。
        // 这些 EXPIRE 的 source_biz_no 同样指向本会员周期，必须一并恢复。
        long amount = restorable;
        int rows = baseMapper.restoreExpiredForRenewal(userId, amount);
        if (rows != 1) {
            throw new ServiceException("续费豆子恢复失败，到期累计数据不一致");
        }
        long balanceAfter = safeBalance(findByUserId(userId));
        writeTransaction(userId, TYPE_RENEW_RESTORE, amount, balanceAfter,
                renewalOrderNo, expiryBizNo,
                auditRemark(remark, "续费恢复=" + amount + "，原到期单=" + expiryBizNo));
        return new BeanRenewalRestoreResult(amount, balanceAfter, false, renewalOrderNo);
    }

    @Override
    public BeanAccountVO getAccount(Long userId) {
        return BeanAccountVO.from(findByUserId(userId));
    }

    @Override
    public Map<Long, Long> balanceMapByUserIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return baseMapper.selectList(new LambdaQueryWrapper<BeanAccount>().in(BeanAccount::getUserId, userIds))
                .stream()
                .collect(Collectors.toMap(BeanAccount::getUserId, BeanAccount::getBalance));
    }

    /**
     * 加豆通用逻辑:校验金额 → 确保账户 → 原子加豆 → 取最新余额记流水。
     *
     * @param changeValue 流水正向变动值(等于 amount)
     */
    private void increase(Long userId, long amount, String type, String bizNo, String remark) {
        requirePositive(amount);
        ensureAccount(userId);
        baseMapper.increaseEarn(userId, amount);
        BeanAccount account = findByUserId(userId);
        writeTransaction(userId, type, amount, account.getBalance(), bizNo, remark);
    }

    /**
     * 扣减通用逻辑:校验金额 → 业务单号幂等校验 → 原子扣减(WHERE balance>=amount)
     * → 影响 0 行抛余额不足 → 取最新余额记流水。流水变动值记为负数。
     */
    private void deductInternal(Long userId, long amount, String type, String bizNo, String remark) {
        requirePositive(amount);
        if (TYPE_DEDUCT.equals(type) && bizNo != null && !bizNo.isBlank()) {
            lockAccount(userId);
            BeanTransaction existing = beanTransactionMapper.selectLatestDeduct(userId, bizNo);
            if (existing != null) {
                if (!Long.valueOf(-amount).equals(existing.getChangeValue())) {
                    throw new ServiceException("业务单号重复且扣豆数量不一致");
                }
                return;
            }
        }
        int rows = baseMapper.deductIfEnough(userId, amount);
        if (rows == 0) {
            throw new ServiceException("豆子余额不足");
        }
        BeanAccount account = findByUserId(userId);
        writeTransaction(userId, type, -amount, account.getBalance(), bizNo, remark);
    }

    /**
     * 写一条流水(变动后余额快照由调用方传入)。
     */
    private void writeTransaction(Long userId, String type, long changeValue, Long balanceAfter,
                                  String bizNo, String remark) {
        writeTransaction(userId, type, changeValue, balanceAfter, bizNo, null, remark);
    }

    /**
     * 写一条可携带来源业务单号的流水。
     */
    private void writeTransaction(Long userId, String type, long changeValue, Long balanceAfter,
                                  String bizNo, String sourceBizNo, String remark) {
        BeanTransaction tx = new BeanTransaction();
        tx.setUserId(userId);
        tx.setType(type);
        tx.setChangeValue(changeValue);
        tx.setBalanceAfter(balanceAfter);
        tx.setBizNo(bizNo);
        tx.setSourceBizNo(sourceBizNo);
        tx.setRemark(remark);
        tx.setCreateTime(LocalDateTime.now());
        beanTransactionMapper.insert(tx);
    }

    private BeanRefundRevokeResult revokeForOrderRefundInternal(Long userId, long requested,
                                                                 boolean revokeAll,
                                                                 String bizNo, String remark) {
        requireUserAndBizNo(userId, bizNo);
        BeanAccount locked = lockAccount(userId);

        BeanTransaction existing = beanTransactionMapper.selectRefundRevoke(userId, bizNo);
        if (existing != null) {
            long revoked = existing.getChangeValue() == null
                    ? 0L : Math.max(0L, -existing.getChangeValue());
            long originalRequested = revokeAll ? revoked : requested;
            return new BeanRefundRevokeResult(originalRequested, revoked,
                    Math.max(0L, originalRequested - revoked),
                    existing.getBalanceAfter() == null ? 0L : existing.getBalanceAfter(), true);
        }

        long balance = safeBalance(locked);
        long target = revokeAll ? balance : requested;
        // 指定订单只回收该笔 ISSUE/RECHARGE 按流水 FIFO 还原后仍未消费的豆子。
        // 这样旧订单已用完后，即使用户后来又充值，也不会错扣后来到账的豆子。
        long refundable = revokeAll ? balance : refundableRemainingForOrder(userId, bizNo, requested);
        long revoked = Math.min(balance, Math.min(target, refundable));
        if (revoked > 0) {
            int rows = baseMapper.revokeForOrderRefund(userId, revoked);
            if (rows != 1) {
                throw new ServiceException("退款豆子权益回收失败");
            }
        }
        long balanceAfter = balance - revoked;
        long shortfall = target - revoked;
        writeTransaction(userId, TYPE_REVOKE, -revoked, balanceAfter, bizNo,
                (revokeAll ? REVOKE_ALL_PREFIX : REVOKE_TARGET_PREFIX) + bizNo,
                auditRemark(remark, "应回收=" + target + "，实际回收=" + revoked
                        + "，未回收=" + shortfall));
        return new BeanRefundRevokeResult(target, revoked, shortfall, balanceAfter, false);
    }

    private BeanRenewalRestoreResult restoreResult(BeanTransaction tx, boolean alreadyProcessed) {
        long restored = tx.getChangeValue() == null ? 0L : Math.max(0L, tx.getChangeValue());
        return new BeanRenewalRestoreResult(restored,
                tx.getBalanceAfter() == null ? 0L : tx.getBalanceAfter(),
                alreadyProcessed, tx.getBizNo());
    }

    /**
     * 使用不可变账户流水还原目标订单的剩余豆子。普通扣减按 FIFO 分摊；新版指定订单退款
     * 只冲销其来源批次。找不到原始发放流水时保守返回 0，绝不猜测性扣除其他来源豆子。
     */
    private long refundableRemainingForOrder(Long userId, String orderNo, long requested) {
        long remaining = replayCreditLots(userId, orderNo).stream()
                .filter(CreditLot::target)
                .mapToLong(CreditLot::remaining)
                .sum();
        return Math.min(requested, remaining);
    }

    /**
     * 重放不可变流水形成来源批次。普通消费优先消耗有期限的会员赠豆，再消耗永久豆；
     * 失败退豆和续费恢复仍回到原批次，保证到期与平台退款只影响对应来源。
     */
    private List<CreditLot> replayCreditLots(Long userId, String targetOrderNo) {
        List<BeanTransaction> ledger = beanTransactionMapper.selectLedgerByUserId(userId);
        if (ledger == null || ledger.isEmpty()) {
            return List.of();
        }
        List<CreditLot> lots = new ArrayList<>();
        Map<String, List<LotConsumption>> deductionAllocations = new HashMap<>();
        Map<String, List<LotConsumption>> expiryAllocations = new HashMap<>();
        for (BeanTransaction tx : ledger) {
            long change = tx.getChangeValue() == null ? 0L : tx.getChangeValue();
            if (change > 0) {
                long unallocatedCredit = change;
                if (TYPE_REFUND.equals(tx.getType()) && tx.getBizNo() != null) {
                    unallocatedCredit = restoreDeductionLots(
                            deductionAllocations.get(tx.getBizNo()), change);
                } else if (TYPE_RENEW_RESTORE.equals(tx.getType())
                        && tx.getSourceBizNo() != null) {
                    // RENEW_RESTORE 不是新的匿名豆批次：它是对同一会员周期 EXPIRE
                    // 分摊的逆操作，必须恢复到原 ISSUE/RECHARGE 批次，否则旧订单退款
                    // 时会找不到它实际剩余的豆子。
                    unallocatedCredit = restoreDeductionLots(
                            expiryAllocations.get(tx.getSourceBizNo()), change);
                }
                if (unallocatedCredit == 0) {
                    continue;
                }
                boolean paidOrderGrant = TYPE_ISSUE.equals(tx.getType())
                        || TYPE_RECHARGE.equals(tx.getType());
                boolean targetGrant = targetOrderNo != null
                        && targetOrderNo.equals(tx.getBizNo()) && paidOrderGrant;
                lots.add(new CreditLot(tx.getBizNo(), unallocatedCredit, targetGrant,
                        paidOrderGrant, TYPE_ISSUE.equals(tx.getType())));
                continue;
            }
            if (change == 0 && TYPE_REVOKE.equals(tx.getType())) {
                markRevokedLots(lots, tx.getSourceBizNo());
            }
            if (change >= 0) {
                continue;
            }

            long debit = -change;
            String source = tx.getSourceBizNo();
            if (TYPE_REVOKE.equals(tx.getType()) && source != null
                    && source.startsWith(REVOKE_TARGET_PREFIX)) {
                String revokedOrder = source.substring(REVOKE_TARGET_PREFIX.length());
                debit = consumeTargetLots(lots, revokedOrder, debit);
            }
            String expirySource = expirySource(tx);
            List<LotConsumption> allocations = (TYPE_DEDUCT.equals(tx.getType())
                    && tx.getBizNo() != null) || expirySource != null
                    ? new ArrayList<>() : null;
            consumeByValidityPriority(lots, debit, allocations);
            if (allocations != null && !allocations.isEmpty()) {
                if (TYPE_DEDUCT.equals(tx.getType()) && tx.getBizNo() != null) {
                    deductionAllocations.computeIfAbsent(tx.getBizNo(), ignored -> new ArrayList<>())
                            .addAll(allocations);
                }
                if (expirySource != null) {
                    expiryAllocations.computeIfAbsent(expirySource, ignored -> new ArrayList<>())
                            .addAll(allocations);
                }
            }
        }
        return lots;
    }

    /**
     * 重放到当前流水，计算目标任务本次 REFUND 还原到已 REVOKE 批次的数量。
     * REVOKE_ALL 取消当时已存在的全部批次（包括已被任务扣走的部分）；
     * TARGET 只取消指定 ISSUE/RECHARGE 订单批次。两者都不标记后来新到账批次。
     */
    private long cancelledTaskRefundAmount(Long userId, String taskBizNo, long requested) {
        List<BeanTransaction> ledger = beanTransactionMapper.selectLedgerByUserId(userId);
        if (ledger == null || ledger.isEmpty()) {
            return 0L;
        }
        List<CreditLot> lots = new ArrayList<>();
        Map<String, List<LotConsumption>> deductionAllocations = new HashMap<>();
        Map<String, List<LotConsumption>> expiryAllocations = new HashMap<>();
        long latestInvalidRestored = 0L;
        for (BeanTransaction tx : ledger) {
            long change = tx.getChangeValue() == null ? 0L : tx.getChangeValue();
            if (change > 0) {
                long unallocated = change;
                if (TYPE_REFUND.equals(tx.getType()) && tx.getBizNo() != null) {
                    RestoreSummary summary = restoreLots(
                            deductionAllocations.get(tx.getBizNo()), change);
                    unallocated = summary.unallocated();
                    if (taskBizNo.equals(tx.getBizNo())) {
                        latestInvalidRestored = summary.revokedRestored();
                    }
                } else if (TYPE_RENEW_RESTORE.equals(tx.getType())
                        && tx.getSourceBizNo() != null) {
                    unallocated = restoreLots(
                            expiryAllocations.get(tx.getSourceBizNo()), change).unallocated();
                }
                if (unallocated > 0) {
                    boolean paid = TYPE_ISSUE.equals(tx.getType()) || TYPE_RECHARGE.equals(tx.getType());
                    lots.add(new CreditLot(tx.getBizNo(), unallocated, false, paid,
                            TYPE_ISSUE.equals(tx.getType())));
                }
                continue;
            }
            if (change == 0 && TYPE_REVOKE.equals(tx.getType())) {
                markRevokedLots(lots, tx.getSourceBizNo());
            }
            if (change >= 0) {
                continue;
            }

            long debit = -change;
            String source = tx.getSourceBizNo();
            if (TYPE_REVOKE.equals(tx.getType()) && source != null) {
                markRevokedLots(lots, source);
                if (source.startsWith(REVOKE_TARGET_PREFIX)) {
                    String orderNo = source.substring(REVOKE_TARGET_PREFIX.length());
                    debit = consumeTargetLots(lots, orderNo, debit);
                }
            }
            String expirySource = expirySource(tx);
            List<LotConsumption> allocations = (TYPE_DEDUCT.equals(tx.getType())
                    && tx.getBizNo() != null) || expirySource != null
                    ? new ArrayList<>() : null;
            consumeByValidityPriority(lots, debit, allocations);
            if (allocations != null && !allocations.isEmpty()) {
                if (TYPE_DEDUCT.equals(tx.getType()) && tx.getBizNo() != null) {
                    deductionAllocations.computeIfAbsent(tx.getBizNo(), ignored -> new ArrayList<>())
                            .addAll(allocations);
                }
                if (expirySource != null) {
                    expiryAllocations.computeIfAbsent(expirySource, ignored -> new ArrayList<>())
                            .addAll(allocations);
                }
            }
        }
        return Math.min(requested, latestInvalidRestored);
    }

    private void markRevokedLots(List<CreditLot> lots, String source) {
        if (source == null) {
            return;
        }
        if (source.startsWith(REVOKE_ALL_PREFIX)) {
            lots.forEach(CreditLot::markRevoked);
            return;
        }
        if (source.startsWith(REVOKE_TARGET_PREFIX)) {
            String orderNo = source.substring(REVOKE_TARGET_PREFIX.length());
            lots.stream()
                    .filter(lot -> lot.paidOrderGrant() && orderNo.equals(lot.bizNo()))
                    .forEach(CreditLot::markRevoked);
        }
    }

    /**
     * 会员终止清零用自身 biz_no 标识周期；旧任务晚到退豆的补充清零
     * 则用 source_biz_no 指回同一周期。两者在账本重放时必须合并为一组可逆分摊。
     */
    private String expirySource(BeanTransaction tx) {
        if (!TYPE_EXPIRE.equals(tx.getType())) {
            return null;
        }
        if (tx.getSourceBizNo() != null && !tx.getSourceBizNo().isBlank()) {
            String source = tx.getSourceBizNo();
            int detailSeparator = source.indexOf('|');
            return detailSeparator < 0 ? source : source.substring(0, detailSeparator);
        }
        String bizNo = tx.getBizNo();
        return bizNo != null && bizNo.startsWith("membership-expire:") ? bizNo : null;
    }

    /** 返回目标批次未能承接的剩余扣减，再交给 FIFO 维持总账平衡。 */
    private long consumeTargetLots(List<CreditLot> lots, String orderNo, long debit) {
        long left = debit;
        for (CreditLot lot : lots) {
            if (left == 0) {
                break;
            }
            if (!lot.paidOrderGrant() || !orderNo.equals(lot.bizNo()) || lot.remaining() <= 0) {
                continue;
            }
            long used = Math.min(left, lot.remaining());
            lot.consume(used);
            left -= used;
        }
        return left;
    }

    private void consumeByValidityPriority(List<CreditLot> lots, long debit,
                                           List<LotConsumption> allocations) {
        long left = debit;
        for (boolean membershipFirst : new boolean[]{true, false}) {
            for (CreditLot lot : lots) {
                if (left == 0) return;
                if (lot.membershipGrant() != membershipFirst || lot.remaining() <= 0) continue;
                long used = Math.min(left, lot.remaining());
                lot.consume(used);
                if (allocations != null) allocations.add(new LotConsumption(lot, used));
                left -= used;
            }
        }
    }

    /** 生成任务失败退豆时，优先恢复该次 DEDUCT 原先消耗的具体批次。 */
    private long restoreDeductionLots(List<LotConsumption> allocations, long credit) {
        return restoreLots(allocations, credit).unallocated();
    }

    private RestoreSummary restoreLots(List<LotConsumption> allocations, long credit) {
        if (allocations == null || allocations.isEmpty()) {
            return new RestoreSummary(credit, 0L);
        }
        long left = credit;
        long revokedRestored = 0L;
        for (int i = allocations.size() - 1; i >= 0 && left > 0; i--) {
            LotConsumption allocation = allocations.get(i);
            long restored = Math.min(left, allocation.restorable());
            if (restored <= 0) {
                continue;
            }
            allocation.lot().restore(restored);
            if (allocation.lot().revoked()) {
                revokedRestored += restored;
            }
            allocation.restore(restored);
            left -= restored;
        }
        return new RestoreSummary(left, revokedRestored);
    }

    private static final class CreditLot {
        private final String bizNo;
        private long remaining;
        private final boolean target;
        private final boolean paidOrderGrant;
        private final boolean membershipGrant;
        private boolean revoked;

        private CreditLot(String bizNo, long remaining, boolean target,
                          boolean paidOrderGrant, boolean membershipGrant) {
            this.bizNo = bizNo;
            this.remaining = remaining;
            this.target = target;
            this.paidOrderGrant = paidOrderGrant;
            this.membershipGrant = membershipGrant;
        }

        String bizNo() {
            return bizNo;
        }

        long remaining() {
            return remaining;
        }

        boolean target() {
            return target;
        }

        boolean paidOrderGrant() {
            return paidOrderGrant;
        }

        boolean membershipGrant() {
            return membershipGrant;
        }

        boolean revoked() {
            return revoked;
        }

        void markRevoked() {
            revoked = true;
        }

        void consume(long amount) {
            remaining -= amount;
        }

        void restore(long amount) {
            remaining += amount;
        }
    }

    private record RestoreSummary(long unallocated, long revokedRestored) {
    }

    private static final class LotConsumption {
        private final CreditLot lot;
        private long restorable;

        private LotConsumption(CreditLot lot, long restorable) {
            this.lot = lot;
            this.restorable = restorable;
        }

        CreditLot lot() {
            return lot;
        }

        long restorable() {
            return restorable;
        }

        void restore(long amount) {
            restorable -= amount;
        }
    }

    private void requireUserAndBizNo(Long userId, String bizNo) {
        if (userId == null) {
            throw new ServiceException("用户ID不能为空");
        }
        if (bizNo == null || bizNo.isBlank()) {
            throw new ServiceException("退款或续费订单号不能为空");
        }
        if (bizNo.length() > 64) {
            throw new ServiceException("退款或续费订单号过长");
        }
    }

    private long safeBalance(BeanAccount account) {
        return account == null || account.getBalance() == null ? 0L : account.getBalance();
    }

    private String auditRemark(String remark, String audit) {
        String value = remark == null || remark.isBlank() ? audit : remark + "；" + audit;
        return value.length() <= MAX_REMARK_LENGTH ? value : value.substring(0, MAX_REMARK_LENGTH);
    }

    /**
     * 按 user_id 查账户(user_id 唯一)。
     */
    private BeanAccount findByUserId(Long userId) {
        return baseMapper.selectOne(new LambdaQueryWrapper<BeanAccount>().eq(BeanAccount::getUserId, userId));
    }

    /**
     * 金额必须为正。
     */
    private void requirePositive(long amount) {
        if (amount <= 0) {
            throw new ServiceException("豆子数量必须大于 0");
        }
    }
}
