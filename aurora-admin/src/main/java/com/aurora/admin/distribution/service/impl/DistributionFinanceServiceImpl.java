package com.aurora.admin.distribution.service.impl;

import com.aurora.admin.distribution.domain.DistributionAccount;
import com.aurora.admin.distribution.domain.DistributionAccountTransaction;
import com.aurora.admin.distribution.domain.DistributionCommission;
import com.aurora.admin.distribution.domain.DistributionConfig;
import com.aurora.admin.distribution.domain.DistributionPartner;
import com.aurora.admin.distribution.domain.DistributionRelation;
import com.aurora.admin.distribution.domain.DistributionWithdrawal;
import com.aurora.admin.distribution.domain.DistributionWithdrawalAllocation;
import com.aurora.admin.distribution.dto.DistributionDtos;
import com.aurora.admin.distribution.mapper.DistributionAccountMapper;
import com.aurora.admin.distribution.mapper.DistributionAccountTransactionMapper;
import com.aurora.admin.distribution.mapper.DistributionCommissionMapper;
import com.aurora.admin.distribution.mapper.DistributionPartnerMapper;
import com.aurora.admin.distribution.mapper.DistributionRelationMapper;
import com.aurora.admin.distribution.mapper.DistributionWithdrawalAllocationMapper;
import com.aurora.admin.distribution.mapper.DistributionWithdrawalMapper;
import com.aurora.admin.distribution.service.IDistributionFinanceService;
import com.aurora.admin.distribution.service.IDistributionPartnerService;
import com.aurora.admin.distribution.storage.DistributionPrivateStorage;
import com.aurora.admin.distribution.support.DistributionConstants;
import com.aurora.admin.distribution.vo.DistributionViews;
import com.aurora.admin.mobile.domain.PayOrder;
import com.aurora.common.core.exception.ServiceException;
import com.aurora.framework.utils.EncryptUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DistributionFinanceServiceImpl implements IDistributionFinanceService {

    private final IDistributionPartnerService partnerService;
    private final DistributionPartnerMapper partnerMapper;
    private final DistributionRelationMapper relationMapper;
    private final DistributionAccountMapper accountMapper;
    private final DistributionCommissionMapper commissionMapper;
    private final DistributionAccountTransactionMapper transactionMapper;
    private final DistributionWithdrawalMapper withdrawalMapper;
    private final DistributionWithdrawalAllocationMapper allocationMapper;
    private final DistributionPrivateStorage privateStorage;
    private final EncryptUtils encryptUtils;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordPaidOrder(PayOrder order) {
        if (order == null || order.getOrderNo() == null || order.getPayTime() == null
                || order.getAmount() == null || order.getAmount() < 0
                || !("MEMBERSHIP".equals(order.getProductType()) || "BEAN_RECHARGE".equals(order.getProductType()))) {
            return;
        }
        if (commissionMapper.selectByOrderNo(order.getOrderNo()) != null) {
            return;
        }

        DistributionConfig config = partnerService.getConfig();
        if (config.getEnabled() != 1) {
            return;
        }
        DistributionRelation relation = relationMapper.selectByReferredUserId(order.getUserId());
        if (relation == null || order.getPayTime().isBefore(relation.getBindTime())) {
            return;
        }
        DistributionPartner partner = partnerMapper.selectById(relation.getPartnerId());
        if (partner == null || !DistributionConstants.PARTNER_ACTIVE.equals(partner.getStatus())
                || order.getPayTime().isBefore(partner.getEnabledTime())) {
            return;
        }

        DistributionAccount account = requireAccountForUpdate(partner.getPartnerId());
        if (commissionMapper.selectByOrderNo(order.getOrderNo()) != null) {
            return;
        }

        long commissionAmount = calculateCommission(order.getAmount(), config.getCommissionRateBps());
        long debtOffset = Math.min(account.getDebtAmount(), commissionAmount);
        long available = commissionAmount - debtOffset;
        LocalDateTime now = LocalDateTime.now();

        DistributionCommission commission = new DistributionCommission();
        commission.setPartnerId(partner.getPartnerId());
        commission.setReferredUserId(order.getUserId());
        commission.setOrderNo(order.getOrderNo());
        commission.setProductType(order.getProductType());
        commission.setProductName(order.getProductName() == null ? "" : order.getProductName());
        commission.setOrderAmount(order.getAmount().longValue());
        commission.setRateBps(config.getCommissionRateBps());
        commission.setCommissionAmount(commissionAmount);
        commission.setAvailableAmount(available);
        commission.setLockedAmount(0L);
        commission.setWithdrawnAmount(0L);
        commission.setReversedAmount(0L);
        commission.setDebtOffsetAmount(debtOffset);
        commission.setStatus(resolveCommissionStatus(commission));
        commission.setPayTime(order.getPayTime());
        commission.setCreateTime(now);
        commission.setUpdateTime(now);
        commissionMapper.insert(commission);

        long originalAvailable = account.getAvailableAmount();
        account.setDebtAmount(account.getDebtAmount() - debtOffset);
        if (debtOffset > 0) {
            insertTransaction(partner.getPartnerId(), DistributionConstants.TX_DEBT_OFFSET, -debtOffset,
                    originalAvailable, account.getWithdrawLockedAmount(), account.getDebtAmount(),
                    order.getOrderNo() + ":debt", order.getOrderNo(), null, "新佣金优先抵扣退款欠款");
        }
        account.setAvailableAmount(originalAvailable + available);
        account.setUpdateTime(now);
        account.setVersion(account.getVersion() + 1);
        accountMapper.updateById(account);
        if (available > 0) {
            insertTransaction(partner.getPartnerId(), DistributionConstants.TX_COMMISSION, available,
                    account.getAvailableAmount(), account.getWithdrawLockedAmount(), account.getDebtAmount(),
                    order.getOrderNo() + ":commission", order.getOrderNo(), null, "订单履约成功，佣金入账");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reverseRefundedOrder(PayOrder order, String refundNo, LocalDateTime refundTime) {
        if (order == null || order.getOrderNo() == null) {
            return;
        }
        DistributionCommission snapshot = commissionMapper.selectByOrderNo(order.getOrderNo());
        if (snapshot == null) {
            return;
        }
        DistributionAccount account = requireAccountForUpdate(snapshot.getPartnerId());
        DistributionCommission commission = commissionMapper.selectByOrderNoForUpdate(order.getOrderNo());
        if (commission == null) {
            return;
        }
        if (commission.getRefundTime() != null || DistributionConstants.COMMISSION_REVERSED.equals(commission.getStatus())) {
            if (refundNo != null && commission.getRefundNo() != null && !refundNo.equals(commission.getRefundNo())) {
                throw new ServiceException("同一佣金收到不同退款编号");
            }
            return;
        }

        List<DistributionWithdrawalAllocation> allocationSnapshots =
                allocationMapper.selectActiveByCommission(commission.getCommissionId());
        allocationSnapshots.stream()
                .map(DistributionWithdrawalAllocation::getWithdrawalId)
                .distinct()
                .sorted()
                .forEach(withdrawalMapper::selectByIdForUpdate);
        List<DistributionWithdrawalAllocation> allocations = new ArrayList<>(
                allocationMapper.selectActiveByCommissionForUpdate(commission.getCommissionId()));
        allocations.sort(Comparator.comparing(DistributionWithdrawalAllocation::getAllocationId));
        long lockedReversed = 0L;
        long paidReversed = 0L;
        for (DistributionWithdrawalAllocation allocation : allocations) {
            if (DistributionConstants.ALLOCATION_LOCKED.equals(allocation.getStatus())) {
                DistributionWithdrawal withdrawal = withdrawalMapper.selectById(allocation.getWithdrawalId());
                if (withdrawal != null && DistributionConstants.WITHDRAW_SUBMITTED.equals(withdrawal.getStatus())) {
                    withdrawal.setStatus(DistributionConstants.WITHDRAW_NEEDS_REVIEW);
                    withdrawal.setUpdateTime(LocalDateTime.now());
                    withdrawalMapper.updateById(withdrawal);
                }
                allocation.setStatus(DistributionConstants.ALLOCATION_REVERSED);
                allocation.setUpdateTime(LocalDateTime.now());
                allocationMapper.updateById(allocation);
                lockedReversed += allocation.getAllocatedAmount();
            } else if (DistributionConstants.ALLOCATION_PAID.equals(allocation.getStatus())) {
                allocation.setStatus(DistributionConstants.ALLOCATION_REVERSED);
                allocation.setUpdateTime(LocalDateTime.now());
                allocationMapper.updateById(allocation);
                paidReversed += allocation.getAllocatedAmount();
            }
        }
        if (lockedReversed != commission.getLockedAmount() || paidReversed != commission.getWithdrawnAmount()) {
            throw new ServiceException("佣金与提现分配金额不一致，已停止退款冲正");
        }

        long debtRestored = commission.getWithdrawnAmount() + commission.getDebtOffsetAmount();
        account.setAvailableAmount(account.getAvailableAmount() - commission.getAvailableAmount());
        account.setWithdrawLockedAmount(account.getWithdrawLockedAmount() - commission.getLockedAmount());
        account.setDebtAmount(account.getDebtAmount() + debtRestored);
        account.setTotalReversed(account.getTotalReversed() + commission.getCommissionAmount());
        ensureAccountNonNegative(account);
        account.setVersion(account.getVersion() + 1);
        account.setUpdateTime(LocalDateTime.now());
        accountMapper.updateById(account);

        commission.setAvailableAmount(0L);
        commission.setLockedAmount(0L);
        commission.setWithdrawnAmount(0L);
        commission.setDebtOffsetAmount(0L);
        commission.setReversedAmount(commission.getCommissionAmount());
        commission.setStatus(DistributionConstants.COMMISSION_REVERSED);
        commission.setRefundNo(refundNo);
        commission.setRefundTime(refundTime == null ? LocalDateTime.now() : refundTime);
        commission.setUpdateTime(LocalDateTime.now());
        commissionMapper.updateById(commission);

        String businessNo = refundNo == null || refundNo.isBlank() ? order.getOrderNo() : refundNo;
        insertTransaction(commission.getPartnerId(), DistributionConstants.TX_REFUND_REVERSE,
                -commission.getCommissionAmount(), account.getAvailableAmount(), account.getWithdrawLockedAmount(),
                account.getDebtAmount(), businessNo, order.getOrderNo(), null, "订单退款，佣金全额冲正");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adjustCommission(Long commissionId, DistributionDtos.CommissionAdjust request, String operator) {
        DistributionCommission snapshot = commissionMapper.selectById(commissionId);
        if (snapshot == null) {
            throw new ServiceException("佣金记录不存在");
        }
        DistributionAccount account = requireAccountForUpdate(snapshot.getPartnerId());
        DistributionCommission commission = commissionMapper.selectByIdForUpdate(commissionId);
        if (commission.getRefundTime() != null || commission.getLockedAmount() > 0 || commission.getWithdrawnAmount() > 0) {
            throw new ServiceException("退款、提现中或已提现佣金不能人工调整");
        }
        if (request.getEffectiveAmount() > commission.getCommissionAmount()) {
            throw new ServiceException("调整金额不能超过原始订单佣金");
        }
        long currentEffective = commission.getCommissionAmount() - commission.getReversedAmount();
        long delta = request.getEffectiveAmount() - currentEffective;
        if (delta == 0) {
            return;
        }
        if (delta < 0) {
            long reduction = -delta;
            long fromAvailable = Math.min(commission.getAvailableAmount(), reduction);
            commission.setAvailableAmount(commission.getAvailableAmount() - fromAvailable);
            account.setAvailableAmount(account.getAvailableAmount() - fromAvailable);
            long fromDebtOffset = reduction - fromAvailable;
            if (fromDebtOffset > commission.getDebtOffsetAmount()) {
                throw new ServiceException("佣金当前可调整金额不足");
            }
            commission.setDebtOffsetAmount(commission.getDebtOffsetAmount() - fromDebtOffset);
            account.setDebtAmount(account.getDebtAmount() + fromDebtOffset);
            commission.setReversedAmount(commission.getReversedAmount() + reduction);
        } else {
            if (delta > commission.getReversedAmount()) {
                throw new ServiceException("只能恢复此前人工调减的佣金");
            }
            long debtOffset = Math.min(account.getDebtAmount(), delta);
            commission.setDebtOffsetAmount(commission.getDebtOffsetAmount() + debtOffset);
            account.setDebtAmount(account.getDebtAmount() - debtOffset);
            commission.setAvailableAmount(commission.getAvailableAmount() + delta - debtOffset);
            account.setAvailableAmount(account.getAvailableAmount() + delta - debtOffset);
            commission.setReversedAmount(commission.getReversedAmount() - delta);
        }
        ensureAccountNonNegative(account);
        commission.setStatus(resolveCommissionStatus(commission));
        commission.setUpdateTime(LocalDateTime.now());
        commissionMapper.updateById(commission);
        account.setVersion(account.getVersion() + 1);
        account.setUpdateTime(LocalDateTime.now());
        accountMapper.updateById(account);
        insertTransaction(commission.getPartnerId(), DistributionConstants.TX_ADJUST, delta,
                account.getAvailableAmount(), account.getWithdrawLockedAmount(), account.getDebtAmount(),
                "commission:" + commissionId + ":" + UUID.randomUUID(), commission.getOrderNo(), null,
                "佣金纠错：" + request.getReason().trim() + "；操作人：" + operator);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long applyWithdrawal(Long userId, DistributionDtos.WithdrawalApply request, MultipartFile receiptImage) {
        DistributionPartner partner = partnerService.requireByUserId(userId);
        DistributionConfig config = partnerService.getConfig();
        if (config.getEnabled() != 1) {
            throw new ServiceException("分销功能暂未开启");
        }
        validateWithdrawalMethod(request, receiptImage);

        String receiptFileKey = null;
        if (!DistributionConstants.METHOD_BANK_CARD.equals(request.getMethodType())) {
            receiptFileKey = privateStorage.saveImage(receiptImage, "receipt", partner.getPartnerId());
            deleteOnRollback(receiptFileKey);
        }

        DistributionAccount account = requireAccountForUpdate(partner.getPartnerId());
        if (request.getApplyAmount() < config.getMinWithdrawAmount()) {
            throw new ServiceException("提现金额低于最低提现金额");
        }
        if (request.getApplyAmount() > account.getAvailableAmount()) {
            throw new ServiceException("可提现余额不足");
        }

        LocalDateTime now = LocalDateTime.now();
        DistributionWithdrawal withdrawal = new DistributionWithdrawal();
        withdrawal.setWithdrawalNo(generateWithdrawalNo());
        withdrawal.setPartnerId(partner.getPartnerId());
        withdrawal.setApplyAmount(request.getApplyAmount());
        withdrawal.setMethodType(request.getMethodType());
        withdrawal.setPayeeName(request.getPayeeName().trim());
        withdrawal.setStatus(DistributionConstants.WITHDRAW_SUBMITTED);
        withdrawal.setReceiptQrFileKey(receiptFileKey);
        if (DistributionConstants.METHOD_BANK_CARD.equals(request.getMethodType())) {
            String bankAccount = normalizeBankAccount(request.getBankAccount());
            withdrawal.setBankName(request.getBankName().trim());
            withdrawal.setBankAccountCipher(encryptUtils.encrypt(bankAccount));
            withdrawal.setBankAccountMasked(EncryptUtils.mask(bankAccount));
        }
        withdrawal.setApplyTime(now);
        withdrawal.setCreateTime(now);
        withdrawal.setUpdateTime(now);
        withdrawalMapper.insert(withdrawal);

        long remaining = request.getApplyAmount();
        List<DistributionCommission> commissions = commissionMapper.selectAvailableForUpdate(partner.getPartnerId());
        for (DistributionCommission commission : commissions) {
            if (remaining == 0) {
                break;
            }
            long allocated = Math.min(remaining, commission.getAvailableAmount());
            commission.setAvailableAmount(commission.getAvailableAmount() - allocated);
            commission.setLockedAmount(commission.getLockedAmount() + allocated);
            commission.setStatus(resolveCommissionStatus(commission));
            commission.setUpdateTime(now);
            commissionMapper.updateById(commission);

            DistributionWithdrawalAllocation allocation = new DistributionWithdrawalAllocation();
            allocation.setWithdrawalId(withdrawal.getWithdrawalId());
            allocation.setCommissionId(commission.getCommissionId());
            allocation.setAllocatedAmount(allocated);
            allocation.setStatus(DistributionConstants.ALLOCATION_LOCKED);
            allocation.setCreateTime(now);
            allocation.setUpdateTime(now);
            allocationMapper.insert(allocation);
            remaining -= allocated;
        }
        if (remaining != 0) {
            throw new ServiceException("佣金明细余额与账户余额不一致，提现已停止");
        }

        account.setAvailableAmount(account.getAvailableAmount() - request.getApplyAmount());
        account.setWithdrawLockedAmount(account.getWithdrawLockedAmount() + request.getApplyAmount());
        ensureAccountNonNegative(account);
        account.setVersion(account.getVersion() + 1);
        account.setUpdateTime(now);
        accountMapper.updateById(account);
        insertTransaction(partner.getPartnerId(), DistributionConstants.TX_WITHDRAW_LOCK,
                -request.getApplyAmount(), account.getAvailableAmount(), account.getWithdrawLockedAmount(),
                account.getDebtAmount(), withdrawal.getWithdrawalNo(), null, withdrawal.getWithdrawalNo(),
                "提交提现，锁定可用佣金");
        return withdrawal.getWithdrawalId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectWithdrawal(Long withdrawalId, DistributionDtos.WithdrawalReject request, String operator) {
        DistributionWithdrawal snapshot = withdrawalMapper.selectById(withdrawalId);
        if (snapshot == null) {
            throw new ServiceException("提现记录不存在");
        }
        DistributionAccount account = requireAccountForUpdate(snapshot.getPartnerId());
        List<DistributionWithdrawalAllocation> allocationSnapshots = allocationMapper.selectByWithdrawal(withdrawalId);
        allocationSnapshots.stream().sorted(Comparator.comparing(DistributionWithdrawalAllocation::getCommissionId))
                .forEach(item -> commissionMapper.selectByIdForUpdate(item.getCommissionId()));
        DistributionWithdrawal withdrawal = withdrawalMapper.selectByIdForUpdate(withdrawalId);
        List<DistributionWithdrawalAllocation> allocations = allocationMapper.selectByWithdrawalForUpdate(withdrawalId);
        if (!(DistributionConstants.WITHDRAW_SUBMITTED.equals(withdrawal.getStatus())
                || DistributionConstants.WITHDRAW_NEEDS_REVIEW.equals(withdrawal.getStatus()))) {
            throw new ServiceException("当前提现状态不能驳回");
        }

        LocalDateTime now = LocalDateTime.now();
        long restored = 0L;
        for (DistributionWithdrawalAllocation allocation : allocations) {
            if (!DistributionConstants.ALLOCATION_LOCKED.equals(allocation.getStatus())) {
                continue;
            }
            DistributionCommission commission = commissionMapper.selectById(allocation.getCommissionId());
            commission.setLockedAmount(commission.getLockedAmount() - allocation.getAllocatedAmount());
            commission.setAvailableAmount(commission.getAvailableAmount() + allocation.getAllocatedAmount());
            commission.setStatus(resolveCommissionStatus(commission));
            commission.setUpdateTime(now);
            commissionMapper.updateById(commission);
            allocation.setStatus(DistributionConstants.ALLOCATION_RESTORED);
            allocation.setUpdateTime(now);
            allocationMapper.updateById(allocation);
            restored += allocation.getAllocatedAmount();
        }

        account.setWithdrawLockedAmount(account.getWithdrawLockedAmount() - restored);
        account.setAvailableAmount(account.getAvailableAmount() + restored);
        ensureAccountNonNegative(account);
        account.setVersion(account.getVersion() + 1);
        account.setUpdateTime(now);
        accountMapper.updateById(account);

        withdrawal.setStatus(DistributionConstants.WITHDRAW_REJECTED);
        withdrawal.setRejectReason(request.getReason().trim());
        withdrawal.setAuditBy(operator);
        withdrawal.setAuditTime(now);
        withdrawal.setUpdateTime(now);
        withdrawalMapper.updateById(withdrawal);
        insertTransaction(withdrawal.getPartnerId(), DistributionConstants.TX_WITHDRAW_RESTORE, restored,
                account.getAvailableAmount(), account.getWithdrawLockedAmount(), account.getDebtAmount(),
                withdrawal.getWithdrawalNo(), null, withdrawal.getWithdrawalNo(), "提现驳回，返还剩余合法佣金");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markWithdrawalPaid(Long withdrawalId, String paymentReference,
            MultipartFile proofImage, String operator) {
        String normalizedPaymentReference = paymentReference == null || paymentReference.isBlank()
                ? null : paymentReference.trim();
        if (normalizedPaymentReference != null && normalizedPaymentReference.length() > 128) {
            throw new ServiceException("打款流水号不能超过128个字符");
        }
        DistributionWithdrawal snapshot = withdrawalMapper.selectById(withdrawalId);
        if (snapshot == null) {
            throw new ServiceException("提现记录不存在");
        }
        String proofFileKey = null;
        if (proofImage != null && !proofImage.isEmpty()) {
            proofFileKey = privateStorage.saveImage(proofImage, "proof", snapshot.getPartnerId());
            deleteOnRollback(proofFileKey);
        }

        DistributionAccount account = requireAccountForUpdate(snapshot.getPartnerId());
        List<DistributionWithdrawalAllocation> allocationSnapshots = allocationMapper.selectByWithdrawal(withdrawalId);
        allocationSnapshots.stream().sorted(Comparator.comparing(DistributionWithdrawalAllocation::getCommissionId))
                .forEach(item -> commissionMapper.selectByIdForUpdate(item.getCommissionId()));
        DistributionWithdrawal withdrawal = withdrawalMapper.selectByIdForUpdate(withdrawalId);
        List<DistributionWithdrawalAllocation> allocations = allocationMapper.selectByWithdrawalForUpdate(withdrawalId);
        if (DistributionConstants.WITHDRAW_NEEDS_REVIEW.equals(withdrawal.getStatus())) {
            throw new ServiceException("该提现受退款影响，必须驳回后重新申请");
        }
        if (!DistributionConstants.WITHDRAW_SUBMITTED.equals(withdrawal.getStatus())) {
            throw new ServiceException("当前提现状态不能确认打款");
        }

        LocalDateTime now = LocalDateTime.now();
        long paid = 0L;
        for (DistributionWithdrawalAllocation allocation : allocations) {
            if (!DistributionConstants.ALLOCATION_LOCKED.equals(allocation.getStatus())) {
                throw new ServiceException("提现分配状态不正确，已停止打款");
            }
            DistributionCommission commission = commissionMapper.selectById(allocation.getCommissionId());
            commission.setLockedAmount(commission.getLockedAmount() - allocation.getAllocatedAmount());
            commission.setWithdrawnAmount(commission.getWithdrawnAmount() + allocation.getAllocatedAmount());
            commission.setStatus(resolveCommissionStatus(commission));
            commission.setUpdateTime(now);
            commissionMapper.updateById(commission);
            allocation.setStatus(DistributionConstants.ALLOCATION_PAID);
            allocation.setUpdateTime(now);
            allocationMapper.updateById(allocation);
            paid += allocation.getAllocatedAmount();
        }
        if (paid != withdrawal.getApplyAmount()) {
            throw new ServiceException("提现分配金额不一致，已停止打款");
        }

        account.setWithdrawLockedAmount(account.getWithdrawLockedAmount() - paid);
        account.setTotalWithdrawn(account.getTotalWithdrawn() + paid);
        ensureAccountNonNegative(account);
        account.setVersion(account.getVersion() + 1);
        account.setUpdateTime(now);
        accountMapper.updateById(account);

        withdrawal.setStatus(DistributionConstants.WITHDRAW_PAID);
        withdrawal.setPaymentReference(normalizedPaymentReference);
        withdrawal.setPaymentProofFileKey(proofFileKey);
        withdrawal.setPayBy(operator);
        withdrawal.setPaidTime(now);
        withdrawal.setAuditTime(now);
        withdrawal.setUpdateTime(now);
        withdrawalMapper.updateById(withdrawal);
        insertTransaction(withdrawal.getPartnerId(), DistributionConstants.TX_WITHDRAW_PAID, -paid,
                account.getAvailableAmount(), account.getWithdrawLockedAmount(), account.getDebtAmount(),
                withdrawal.getWithdrawalNo(), null, withdrawal.getWithdrawalNo(), "后台确认线下打款");
    }

    @Override
    public List<DistributionViews.WithdrawalReceiptThumbnail> withdrawalReceiptThumbnails(
            List<Long> withdrawalIds) {
        if (withdrawalIds == null || withdrawalIds.isEmpty()) {
            return List.of();
        }
        if (withdrawalIds.size() > 50 || withdrawalIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new ServiceException("收款码缩略图查询范围不正确");
        }
        List<Long> normalizedIds = withdrawalIds.stream().distinct().toList();
        List<DistributionViews.WithdrawalReceiptThumbnail> result = new ArrayList<>();
        for (DistributionWithdrawal withdrawal : withdrawalMapper.selectBatchIds(normalizedIds)) {
            if (!(DistributionConstants.METHOD_WECHAT_QR.equals(withdrawal.getMethodType())
                    || DistributionConstants.METHOD_ALIPAY_QR.equals(withdrawal.getMethodType()))
                    || withdrawal.getReceiptQrFileKey() == null) {
                continue;
            }
            String imageData = privateStorage.readThumbnailDataUri(withdrawal.getReceiptQrFileKey());
            if (imageData == null) {
                continue;
            }
            DistributionViews.WithdrawalReceiptThumbnail thumbnail =
                    new DistributionViews.WithdrawalReceiptThumbnail();
            thumbnail.setWithdrawalId(withdrawal.getWithdrawalId());
            thumbnail.setImageData(imageData);
            result.add(thumbnail);
        }
        return result;
    }

    @Override
    public DistributionViews.WithdrawalPaymentProfile withdrawalPaymentProfile(Long withdrawalId) {
        DistributionWithdrawal withdrawal = withdrawalMapper.selectById(withdrawalId);
        if (withdrawal == null) {
            throw new ServiceException("提现记录不存在", 404);
        }
        DistributionViews.WithdrawalPaymentProfile result = new DistributionViews.WithdrawalPaymentProfile();
        result.setWithdrawalId(withdrawalId);
        result.setMethodType(withdrawal.getMethodType());
        result.setPayeeName(withdrawal.getPayeeName());
        result.setBankName(withdrawal.getBankName());
        result.setBankAccount(withdrawal.getBankAccountCipher() == null
                ? null : encryptUtils.decrypt(withdrawal.getBankAccountCipher()));
        result.setReceiptImageData(privateStorage.readDataUri(withdrawal.getReceiptQrFileKey()));
        return result;
    }

    @Override
    public DistributionViews.WithdrawalSensitive sensitiveWithdrawal(Long withdrawalId, Long ownerPartnerId) {
        DistributionWithdrawal withdrawal = withdrawalMapper.selectById(withdrawalId);
        if (withdrawal == null || (ownerPartnerId != null && !ownerPartnerId.equals(withdrawal.getPartnerId()))) {
            throw new ServiceException("提现记录不存在", 404);
        }
        DistributionViews.WithdrawalSensitive result = new DistributionViews.WithdrawalSensitive();
        result.setWithdrawalId(withdrawalId);
        result.setMethodType(withdrawal.getMethodType());
        result.setPayeeName(withdrawal.getPayeeName());
        result.setBankName(withdrawal.getBankName());
        result.setBankAccount(withdrawal.getBankAccountCipher() == null
                ? null : encryptUtils.decrypt(withdrawal.getBankAccountCipher()));
        result.setReceiptImageData(privateStorage.readDataUri(withdrawal.getReceiptQrFileKey()));
        result.setPaymentProofImageData(privateStorage.readDataUri(withdrawal.getPaymentProofFileKey()));
        return result;
    }

    private DistributionAccount requireAccountForUpdate(Long partnerId) {
        DistributionAccount account = accountMapper.selectForUpdate(partnerId);
        if (account == null) {
            throw new ServiceException("分销资金账户不存在");
        }
        return account;
    }

    private long calculateCommission(long orderAmount, int rateBps) {
        try {
            return Math.multiplyExact(orderAmount, rateBps) / 10000L;
        } catch (ArithmeticException e) {
            throw new ServiceException("佣金计算金额超出范围");
        }
    }

    private void validateWithdrawalMethod(DistributionDtos.WithdrawalApply request, MultipartFile receiptImage) {
        if (!Boolean.TRUE.equals(request.getPrivacyAccepted())) {
            throw new ServiceException("请确认并同意收款资料使用说明");
        }
        if (DistributionConstants.METHOD_BANK_CARD.equals(request.getMethodType())) {
            if (request.getBankName() == null || request.getBankName().isBlank()
                    || request.getBankAccount() == null || request.getBankAccount().isBlank()) {
                throw new ServiceException("银行卡提现必须填写银行名称和银行卡号");
            }
            normalizeBankAccount(request.getBankAccount());
        } else if (receiptImage == null || receiptImage.isEmpty()) {
            throw new ServiceException("收款码提现必须上传收款码");
        }
    }

    private String normalizeBankAccount(String value) {
        String normalized = value == null ? "" : value.replaceAll("[ -]", "");
        if (!normalized.matches("\\d{8,32}")) {
            throw new ServiceException("银行卡号格式不正确");
        }
        return normalized;
    }

    private void ensureAccountNonNegative(DistributionAccount account) {
        if (account.getAvailableAmount() < 0 || account.getWithdrawLockedAmount() < 0
                || account.getTotalWithdrawn() < 0 || account.getTotalReversed() < 0
                || account.getDebtAmount() < 0) {
            throw new ServiceException("分销账户金额校验失败");
        }
    }

    private String resolveCommissionStatus(DistributionCommission commission) {
        if (commission.getCommissionAmount() > 0
                && commission.getReversedAmount().equals(commission.getCommissionAmount())) {
            return DistributionConstants.COMMISSION_REVERSED;
        }
        int nonZero = 0;
        nonZero += commission.getAvailableAmount() > 0 ? 1 : 0;
        nonZero += commission.getLockedAmount() > 0 ? 1 : 0;
        nonZero += commission.getWithdrawnAmount() > 0 ? 1 : 0;
        nonZero += commission.getDebtOffsetAmount() > 0 ? 1 : 0;
        nonZero += commission.getReversedAmount() > 0 ? 1 : 0;
        if (nonZero > 1 || commission.getDebtOffsetAmount() > 0 || commission.getReversedAmount() > 0) {
            return DistributionConstants.COMMISSION_PARTIAL;
        }
        if (commission.getLockedAmount() > 0) {
            return DistributionConstants.COMMISSION_LOCKED;
        }
        if (commission.getWithdrawnAmount() > 0) {
            return DistributionConstants.COMMISSION_WITHDRAWN;
        }
        return DistributionConstants.COMMISSION_AVAILABLE;
    }

    private void insertTransaction(Long partnerId, String type, long changeAmount,
            long availableAfter, long lockedAfter, long debtAfter, String bizNo,
            String sourceOrderNo, String withdrawalNo, String remark) {
        DistributionAccountTransaction transaction = new DistributionAccountTransaction();
        transaction.setPartnerId(partnerId);
        transaction.setType(type);
        transaction.setChangeAmount(changeAmount);
        transaction.setAvailableAfter(availableAfter);
        transaction.setLockedAfter(lockedAfter);
        transaction.setDebtAfter(debtAfter);
        transaction.setBizNo(bizNo);
        transaction.setSourceOrderNo(sourceOrderNo);
        transaction.setWithdrawalNo(withdrawalNo);
        transaction.setRemark(remark);
        transaction.setCreateTime(LocalDateTime.now());
        transactionMapper.insert(transaction);
    }

    private String generateWithdrawalNo() {
        return "TX" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }

    private void deleteOnRollback(String fileKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    privateStorage.deleteQuietly(fileKey);
                }
            }
        });
    }
}
