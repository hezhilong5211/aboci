package com.aurora.admin.distribution.service;

import com.aurora.admin.distribution.dto.DistributionDtos;
import com.aurora.admin.distribution.vo.DistributionViews;
import com.aurora.admin.mobile.domain.PayOrder;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface IDistributionFinanceService {
    void recordPaidOrder(PayOrder order);

    void reverseRefundedOrder(PayOrder order, String refundNo, java.time.LocalDateTime refundTime);

    void adjustCommission(Long commissionId, DistributionDtos.CommissionAdjust request, String operator);

    Long applyWithdrawal(Long userId, DistributionDtos.WithdrawalApply request, MultipartFile receiptImage);

    void rejectWithdrawal(Long withdrawalId, DistributionDtos.WithdrawalReject request, String operator);

    void markWithdrawalPaid(Long withdrawalId, String paymentReference, MultipartFile proofImage, String operator);

    List<DistributionViews.WithdrawalReceiptThumbnail> withdrawalReceiptThumbnails(List<Long> withdrawalIds);

    DistributionViews.WithdrawalPaymentProfile withdrawalPaymentProfile(Long withdrawalId);

    DistributionViews.WithdrawalSensitive sensitiveWithdrawal(Long withdrawalId, Long ownerPartnerId);
}
