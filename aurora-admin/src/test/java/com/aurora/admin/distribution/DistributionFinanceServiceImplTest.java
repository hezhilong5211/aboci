package com.aurora.admin.distribution;

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
import com.aurora.admin.distribution.service.IDistributionPartnerService;
import com.aurora.admin.distribution.service.impl.DistributionFinanceServiceImpl;
import com.aurora.admin.distribution.storage.DistributionPrivateStorage;
import com.aurora.admin.mobile.domain.PayOrder;
import com.aurora.framework.utils.EncryptUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DistributionFinanceServiceImplTest {

    @Mock private IDistributionPartnerService partnerService;
    @Mock private DistributionPartnerMapper partnerMapper;
    @Mock private DistributionRelationMapper relationMapper;
    @Mock private DistributionAccountMapper accountMapper;
    @Mock private DistributionCommissionMapper commissionMapper;
    @Mock private DistributionAccountTransactionMapper transactionMapper;
    @Mock private DistributionWithdrawalMapper withdrawalMapper;
    @Mock private DistributionWithdrawalAllocationMapper allocationMapper;
    @Mock private DistributionPrivateStorage privateStorage;
    @Mock private EncryptUtils encryptUtils;

    private DistributionFinanceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DistributionFinanceServiceImpl(partnerService, partnerMapper, relationMapper, accountMapper,
                commissionMapper, transactionMapper, withdrawalMapper, allocationMapper, privateStorage, encryptUtils);
    }

    @Test
    void paidOrderFloorsCommissionAndOffsetsDebtBeforeAvailableBalance() {
        PayOrder order = paidOrder("O-100", 12345, 22L);
        DistributionConfig config = new DistributionConfig();
        config.setEnabled(1);
        config.setCommissionRateBps(333);
        when(partnerService.getConfig()).thenReturn(config);
        DistributionRelation relation = new DistributionRelation();
        relation.setPartnerId(7L);
        relation.setBindTime(order.getPayTime().minusMinutes(1));
        when(relationMapper.selectByReferredUserId(22L)).thenReturn(relation);
        DistributionPartner partner = new DistributionPartner();
        partner.setPartnerId(7L);
        partner.setStatus("ACTIVE");
        partner.setEnabledTime(order.getPayTime().minusDays(1));
        when(partnerMapper.selectById(7L)).thenReturn(partner);
        DistributionAccount account = account(7L, 0L, 0L, 100L);
        when(accountMapper.selectForUpdate(7L)).thenReturn(account);
        when(commissionMapper.selectByOrderNo("O-100")).thenReturn(null);

        service.recordPaidOrder(order);

        ArgumentCaptor<DistributionCommission> commissionCaptor = ArgumentCaptor.forClass(DistributionCommission.class);
        verify(commissionMapper).insert(commissionCaptor.capture());
        DistributionCommission commission = commissionCaptor.getValue();
        assertEquals(411L, commission.getCommissionAmount());
        assertEquals(100L, commission.getDebtOffsetAmount());
        assertEquals(311L, commission.getAvailableAmount());
        assertEquals(311L, account.getAvailableAmount());
        assertEquals(0L, account.getDebtAmount());

        ArgumentCaptor<DistributionAccountTransaction> transactionCaptor =
                ArgumentCaptor.forClass(DistributionAccountTransaction.class);
        verify(transactionMapper, times(2)).insert(transactionCaptor.capture());
        assertEquals("DEBT_OFFSET", transactionCaptor.getAllValues().get(0).getType());
        assertEquals(-100L, transactionCaptor.getAllValues().get(0).getChangeAmount());
        assertEquals("COMMISSION", transactionCaptor.getAllValues().get(1).getType());
        assertEquals(311L, transactionCaptor.getAllValues().get(1).getChangeAmount());
    }

    @Test
    void refundReversesAvailableLockedPaidAndDebtOffsetParts() {
        PayOrder order = paidOrder("O-200", 10000, 22L);
        DistributionCommission commission = new DistributionCommission();
        commission.setCommissionId(88L);
        commission.setPartnerId(7L);
        commission.setOrderNo("O-200");
        commission.setCommissionAmount(1000L);
        commission.setAvailableAmount(100L);
        commission.setLockedAmount(200L);
        commission.setWithdrawnAmount(300L);
        commission.setDebtOffsetAmount(400L);
        commission.setReversedAmount(0L);
        commission.setStatus("PARTIAL");
        when(commissionMapper.selectByOrderNo("O-200")).thenReturn(commission);
        when(commissionMapper.selectByOrderNoForUpdate("O-200")).thenReturn(commission);
        DistributionAccount account = account(7L, 500L, 400L, 50L);
        account.setTotalWithdrawn(300L);
        when(accountMapper.selectForUpdate(7L)).thenReturn(account);

        DistributionWithdrawalAllocation locked = allocation(1L, 61L, 88L, 200L, "LOCKED");
        DistributionWithdrawalAllocation paid = allocation(2L, 62L, 88L, 300L, "PAID");
        when(allocationMapper.selectActiveByCommission(88L)).thenReturn(List.of(locked, paid));
        when(allocationMapper.selectActiveByCommissionForUpdate(88L)).thenReturn(List.of(locked, paid));
        DistributionWithdrawal withdrawal = new DistributionWithdrawal();
        withdrawal.setWithdrawalId(61L);
        withdrawal.setStatus("SUBMITTED");
        when(withdrawalMapper.selectById(61L)).thenReturn(withdrawal);

        service.reverseRefundedOrder(order, "R-200", LocalDateTime.now());

        assertEquals(400L, account.getAvailableAmount());
        assertEquals(200L, account.getWithdrawLockedAmount());
        assertEquals(750L, account.getDebtAmount());
        assertEquals(1000L, account.getTotalReversed());
        assertEquals(1000L, commission.getReversedAmount());
        assertEquals(0L, commission.getAvailableAmount());
        assertEquals(0L, commission.getLockedAmount());
        assertEquals(0L, commission.getWithdrawnAmount());
        assertEquals(0L, commission.getDebtOffsetAmount());
        assertEquals("REVERSED", commission.getStatus());
        assertEquals("NEEDS_REVIEW", withdrawal.getStatus());
        assertEquals("REVERSED", locked.getStatus());
        assertEquals("REVERSED", paid.getStatus());
        verify(transactionMapper).insert(any(DistributionAccountTransaction.class));
    }

    @Test
    void bankWithdrawalLocksAvailableCommissionsInFifoOrder() {
        DistributionPartner partner = new DistributionPartner();
        partner.setPartnerId(7L);
        partner.setStatus("DISABLED");
        when(partnerService.requireByUserId(22L)).thenReturn(partner);
        DistributionConfig config = new DistributionConfig();
        config.setEnabled(1);
        config.setMinWithdrawAmount(100L);
        when(partnerService.getConfig()).thenReturn(config);
        DistributionAccount account = account(7L, 1000L, 0L, 0L);
        when(accountMapper.selectForUpdate(7L)).thenReturn(account);
        DistributionCommission first = commission(1L, 300L, 300L, 0L, 0L);
        DistributionCommission second = commission(2L, 700L, 700L, 0L, 0L);
        when(commissionMapper.selectAvailableForUpdate(7L)).thenReturn(List.of(first, second));
        when(encryptUtils.encrypt("6222020202020202")).thenReturn("ENC2:test");
        doAnswer(invocation -> {
            invocation.getArgument(0, DistributionWithdrawal.class).setWithdrawalId(50L);
            return 1;
        }).when(withdrawalMapper).insert(any(DistributionWithdrawal.class));

        DistributionDtos.WithdrawalApply request = bankWithdrawal(500L);
        Long withdrawalId = service.applyWithdrawal(22L, request, null);

        assertEquals(50L, withdrawalId);
        assertEquals(500L, account.getAvailableAmount());
        assertEquals(500L, account.getWithdrawLockedAmount());
        assertEquals(0L, first.getAvailableAmount());
        assertEquals(300L, first.getLockedAmount());
        assertEquals(500L, second.getAvailableAmount());
        assertEquals(200L, second.getLockedAmount());
        ArgumentCaptor<DistributionWithdrawalAllocation> allocationCaptor =
                ArgumentCaptor.forClass(DistributionWithdrawalAllocation.class);
        verify(allocationMapper, times(2)).insert(allocationCaptor.capture());
        assertEquals(300L, allocationCaptor.getAllValues().get(0).getAllocatedAmount());
        assertEquals(200L, allocationCaptor.getAllValues().get(1).getAllocatedAmount());
        ArgumentCaptor<DistributionWithdrawal> withdrawalCaptor =
                ArgumentCaptor.forClass(DistributionWithdrawal.class);
        verify(withdrawalMapper).insert(withdrawalCaptor.capture());
        assertEquals("ENC2:test", withdrawalCaptor.getValue().getBankAccountCipher());
        assertEquals("6222****0202", withdrawalCaptor.getValue().getBankAccountMasked());
    }

    @Test
    void rejectNeedsReviewWithdrawalOnlyRestoresStillLockedAllocations() {
        DistributionWithdrawal withdrawal = new DistributionWithdrawal();
        withdrawal.setWithdrawalId(50L);
        withdrawal.setWithdrawalNo("TX-50");
        withdrawal.setPartnerId(7L);
        withdrawal.setApplyAmount(500L);
        withdrawal.setStatus("NEEDS_REVIEW");
        when(withdrawalMapper.selectById(50L)).thenReturn(withdrawal);
        when(withdrawalMapper.selectByIdForUpdate(50L)).thenReturn(withdrawal);
        DistributionAccount account = account(7L, 100L, 700L, 0L);
        when(accountMapper.selectForUpdate(7L)).thenReturn(account);
        DistributionWithdrawalAllocation reversed = allocation(1L, 50L, 11L, 200L, "REVERSED");
        DistributionWithdrawalAllocation locked = allocation(2L, 50L, 12L, 300L, "LOCKED");
        when(allocationMapper.selectByWithdrawal(50L)).thenReturn(List.of(reversed, locked));
        when(allocationMapper.selectByWithdrawalForUpdate(50L)).thenReturn(List.of(reversed, locked));
        DistributionCommission commission = commission(12L, 300L, 0L, 300L, 0L);
        when(commissionMapper.selectById(12L)).thenReturn(commission);

        DistributionDtos.WithdrawalReject request = new DistributionDtos.WithdrawalReject();
        request.setReason("退款影响余额");
        service.rejectWithdrawal(50L, request, "admin");

        assertEquals(400L, account.getAvailableAmount());
        assertEquals(400L, account.getWithdrawLockedAmount());
        assertEquals(300L, commission.getAvailableAmount());
        assertEquals(0L, commission.getLockedAmount());
        assertEquals("RESTORED", locked.getStatus());
        assertEquals("REVERSED", reversed.getStatus());
        assertEquals("REJECTED", withdrawal.getStatus());
        assertEquals("退款影响余额", withdrawal.getRejectReason());
    }

    @Test
    void paidWithdrawalMovesLockedAmountsAndStoresProofReference() {
        DistributionWithdrawal withdrawal = new DistributionWithdrawal();
        withdrawal.setWithdrawalId(60L);
        withdrawal.setWithdrawalNo("TX-60");
        withdrawal.setPartnerId(7L);
        withdrawal.setApplyAmount(500L);
        withdrawal.setStatus("SUBMITTED");
        when(withdrawalMapper.selectById(60L)).thenReturn(withdrawal);
        when(withdrawalMapper.selectByIdForUpdate(60L)).thenReturn(withdrawal);
        when(privateStorage.saveImage(any(), any(), anyLong())).thenReturn("proof/7/proof.png");
        DistributionAccount account = account(7L, 100L, 500L, 0L);
        account.setTotalWithdrawn(200L);
        when(accountMapper.selectForUpdate(7L)).thenReturn(account);
        DistributionWithdrawalAllocation firstAllocation = allocation(1L, 60L, 21L, 200L, "LOCKED");
        DistributionWithdrawalAllocation secondAllocation = allocation(2L, 60L, 22L, 300L, "LOCKED");
        when(allocationMapper.selectByWithdrawal(60L)).thenReturn(List.of(firstAllocation, secondAllocation));
        when(allocationMapper.selectByWithdrawalForUpdate(60L)).thenReturn(List.of(firstAllocation, secondAllocation));
        DistributionCommission first = commission(21L, 200L, 0L, 200L, 0L);
        DistributionCommission second = commission(22L, 300L, 0L, 300L, 0L);
        when(commissionMapper.selectById(21L)).thenReturn(first);
        when(commissionMapper.selectById(22L)).thenReturn(second);

        service.markWithdrawalPaid(60L, "PAY-20260721-001", anyImage(), "admin");

        assertEquals(0L, account.getWithdrawLockedAmount());
        assertEquals(700L, account.getTotalWithdrawn());
        assertEquals(200L, first.getWithdrawnAmount());
        assertEquals(300L, second.getWithdrawnAmount());
        assertEquals("PAID", firstAllocation.getStatus());
        assertEquals("PAID", secondAllocation.getStatus());
        assertEquals("PAID", withdrawal.getStatus());
        assertEquals("PAY-20260721-001", withdrawal.getPaymentReference());
        assertEquals("proof/7/proof.png", withdrawal.getPaymentProofFileKey());
    }

    @Test
    void paidWithdrawalAllowsMissingPaymentReferenceAndProof() {
        DistributionWithdrawal withdrawal = prepareSubmittedWithdrawal(63L);

        service.markWithdrawalPaid(63L, "  ", null, "admin");

        assertEquals("PAID", withdrawal.getStatus());
        assertNull(withdrawal.getPaymentReference());
        assertNull(withdrawal.getPaymentProofFileKey());
        verify(privateStorage, never()).saveImage(any(), any(), anyLong());
    }

    @Test
    void paidWithdrawalAllowsOnlyPaymentReference() {
        DistributionWithdrawal withdrawal = prepareSubmittedWithdrawal(64L);

        service.markWithdrawalPaid(64L, " PAY-ONLY ", null, "admin");

        assertEquals("PAY-ONLY", withdrawal.getPaymentReference());
        assertNull(withdrawal.getPaymentProofFileKey());
    }

    @Test
    void paidWithdrawalAllowsOnlyPaymentProof() {
        DistributionWithdrawal withdrawal = prepareSubmittedWithdrawal(65L);
        when(privateStorage.saveImage(any(), any(), anyLong())).thenReturn("proof/7/only-proof.png");

        service.markWithdrawalPaid(65L, null, anyImage(), "admin");

        assertNull(withdrawal.getPaymentReference());
        assertEquals("proof/7/only-proof.png", withdrawal.getPaymentProofFileKey());
    }

    @Test
    void returnsPrivateReceiptThumbnailForQrWithdrawal() {
        DistributionWithdrawal withdrawal = new DistributionWithdrawal();
        withdrawal.setWithdrawalId(66L);
        withdrawal.setMethodType("WECHAT_QR");
        withdrawal.setReceiptQrFileKey("receipt/7/code.png");
        when(withdrawalMapper.selectBatchIds(List.of(66L))).thenReturn(List.of(withdrawal));
        when(privateStorage.readThumbnailDataUri("receipt/7/code.png"))
                .thenReturn("data:image/jpeg;base64,thumb");

        var result = service.withdrawalReceiptThumbnails(List.of(66L));

        assertEquals(1, result.size());
        assertEquals(66L, result.get(0).getWithdrawalId());
        assertEquals("data:image/jpeg;base64,thumb", result.get(0).getImageData());
    }

    @Test
    void returnsDecryptedBankPaymentProfile() {
        DistributionWithdrawal withdrawal = new DistributionWithdrawal();
        withdrawal.setWithdrawalId(67L);
        withdrawal.setMethodType("BANK_CARD");
        withdrawal.setPayeeName("张三");
        withdrawal.setBankName("中国工商银行");
        withdrawal.setBankAccountCipher("ENC2:test");
        when(withdrawalMapper.selectById(67L)).thenReturn(withdrawal);
        when(encryptUtils.decrypt("ENC2:test")).thenReturn("6222020202020202");

        var result = service.withdrawalPaymentProfile(67L);

        assertEquals("BANK_CARD", result.getMethodType());
        assertEquals("张三", result.getPayeeName());
        assertEquals("中国工商银行", result.getBankName());
        assertEquals("6222020202020202", result.getBankAccount());
        assertNull(result.getReceiptImageData());
    }

    private PayOrder paidOrder(String orderNo, int amount, long userId) {
        PayOrder order = new PayOrder();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setAmount(amount);
        order.setProductType("MEMBERSHIP");
        order.setProductName("年度会员");
        order.setPayTime(LocalDateTime.now());
        return order;
    }

    private DistributionAccount account(long partnerId, long available, long locked, long debt) {
        DistributionAccount account = new DistributionAccount();
        account.setPartnerId(partnerId);
        account.setAvailableAmount(available);
        account.setWithdrawLockedAmount(locked);
        account.setTotalWithdrawn(0L);
        account.setTotalReversed(0L);
        account.setDebtAmount(debt);
        account.setVersion(0);
        account.setUpdateTime(LocalDateTime.now());
        return account;
    }

    private DistributionWithdrawalAllocation allocation(long id, long withdrawalId, long commissionId,
            long amount, String status) {
        DistributionWithdrawalAllocation allocation = new DistributionWithdrawalAllocation();
        allocation.setAllocationId(id);
        allocation.setWithdrawalId(withdrawalId);
        allocation.setCommissionId(commissionId);
        allocation.setAllocatedAmount(amount);
        allocation.setStatus(status);
        return allocation;
    }

    private DistributionCommission commission(long id, long amount, long available, long locked, long withdrawn) {
        DistributionCommission commission = new DistributionCommission();
        commission.setCommissionId(id);
        commission.setCommissionAmount(amount);
        commission.setAvailableAmount(available);
        commission.setLockedAmount(locked);
        commission.setWithdrawnAmount(withdrawn);
        commission.setDebtOffsetAmount(0L);
        commission.setReversedAmount(0L);
        commission.setStatus("AVAILABLE");
        return commission;
    }

    private DistributionDtos.WithdrawalApply bankWithdrawal(long amount) {
        DistributionDtos.WithdrawalApply request = new DistributionDtos.WithdrawalApply();
        request.setApplyAmount(amount);
        request.setMethodType("BANK_CARD");
        request.setPayeeName("张三");
        request.setBankName("中国工商银行");
        request.setBankAccount("6222 0202 0202 0202");
        request.setPrivacyAccepted(true);
        return request;
    }

    private DistributionWithdrawal prepareSubmittedWithdrawal(long withdrawalId) {
        DistributionWithdrawal withdrawal = new DistributionWithdrawal();
        withdrawal.setWithdrawalId(withdrawalId);
        withdrawal.setWithdrawalNo("TX-" + withdrawalId);
        withdrawal.setPartnerId(7L);
        withdrawal.setApplyAmount(500L);
        withdrawal.setStatus("SUBMITTED");
        when(withdrawalMapper.selectById(withdrawalId)).thenReturn(withdrawal);
        when(withdrawalMapper.selectByIdForUpdate(withdrawalId)).thenReturn(withdrawal);
        DistributionAccount account = account(7L, 100L, 500L, 0L);
        when(accountMapper.selectForUpdate(7L)).thenReturn(account);
        DistributionWithdrawalAllocation allocation = allocation(
                withdrawalId, withdrawalId, 21L, 500L, "LOCKED");
        when(allocationMapper.selectByWithdrawal(withdrawalId)).thenReturn(List.of(allocation));
        when(allocationMapper.selectByWithdrawalForUpdate(withdrawalId)).thenReturn(List.of(allocation));
        DistributionCommission commission = commission(21L, 500L, 0L, 500L, 0L);
        when(commissionMapper.selectById(21L)).thenReturn(commission);
        return withdrawal;
    }

    private org.springframework.web.multipart.MultipartFile anyImage() {
        return new org.springframework.mock.web.MockMultipartFile(
                "proof", "proof.png", "image/png", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47});
    }
}
