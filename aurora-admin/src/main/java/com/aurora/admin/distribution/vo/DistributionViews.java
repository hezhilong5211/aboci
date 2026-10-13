package com.aurora.admin.distribution.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 分销接口返回对象集合。
 */
public final class DistributionViews {

    @Data
    public static class Overview {
        private Long partnerCount;
        private Long promotedUserCount;
        private Long paidUserCount;
        private Long validOrderCount;
        private Long validOrderAmount;
        private Long validCommissionAmount;
        private Long unwithdrawnAmount;
        private Long withdrawnAmount;
        private Long debtAmount;
        private List<WithdrawalRow> recentWithdrawals;
        private List<PartnerRow> ranking;
    }

    @Data
    public static class PartnerCandidate {
        private Long userId;
        private String userNo;
        private String nickName;
        private String phoneMasked;
        private String avatarUrl;
        private Boolean alreadyPartner;
    }

    @Data
    public static class PartnerRow {
        private Long partnerId;
        private String partnerNo;
        private Long userId;
        private String userNo;
        private String nickName;
        private String phoneMasked;
        private String avatarUrl;
        private String status;
        private LocalDateTime enabledTime;
        private LocalDateTime disabledTime;
        private Long visitCount;
        private Long promotedUserCount;
        private Long paidUserCount;
        private Long validOrderCount;
        private Long validOrderAmount;
        private Long validCommissionAmount;
        private Long availableAmount;
        private Long lockedAmount;
        private Long withdrawnAmount;
        private Long debtAmount;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class PartnerDetail extends PartnerRow {
        private String inviteCode;
        private String remark;
        private LocalDateTime createTime;
    }

    @Data
    public static class PromotionUser {
        private Long userId;
        private String nickName;
        private String avatarUrl;
        private String phoneMasked;
        private LocalDateTime bindTime;
        private Long orderCount;
        private Long orderAmount;
        private Long commissionAmount;
    }

    @Data
    public static class PromotionOrder {
        private Long commissionId;
        private String orderNo;
        private String userNo;
        private String phoneMasked;
        private String productType;
        private String productName;
        private Long orderAmount;
        private Integer rateBps;
        private Long commissionAmount;
        private String status;
        private LocalDateTime payTime;
        private LocalDateTime refundTime;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class CommissionRow extends PromotionOrder {
        private Long partnerId;
        private String partnerNo;
        private String partnerName;
        private Long availableAmount;
        private Long lockedAmount;
        private Long withdrawnAmount;
        private Long reversedAmount;
        private Long debtOffsetAmount;
        private String refundNo;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class CommissionDetail extends CommissionRow {
        private List<TransactionRow> transactions;
    }

    @Data
    public static class TransactionRow {
        private Long transactionId;
        private String type;
        private Long changeAmount;
        private Long availableAfter;
        private Long lockedAfter;
        private Long debtAfter;
        private String bizNo;
        private String sourceOrderNo;
        private String withdrawalNo;
        private String remark;
        private LocalDateTime createTime;
    }

    @Data
    public static class WithdrawalRow {
        private Long withdrawalId;
        private String withdrawalNo;
        private Long partnerId;
        private String partnerNo;
        private String partnerName;
        private String phoneMasked;
        private Long applyAmount;
        private String methodType;
        private String payeeName;
        private String bankName;
        private String bankAccountMasked;
        private Boolean receiptAvailable;
        private String status;
        private String rejectReason;
        private String paymentReferenceMasked;
        private Boolean paymentProofAvailable;
        private LocalDateTime applyTime;
        private LocalDateTime auditTime;
        private LocalDateTime paidTime;
        private String auditBy;
        private String payBy;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class WithdrawalDetail extends WithdrawalRow {
        private List<TransactionRow> transactions;
    }

    @Data
    public static class WithdrawalSensitive {
        private Long withdrawalId;
        private String methodType;
        private String payeeName;
        private String bankName;
        private String bankAccount;
        private String receiptImageData;
        private String paymentProofImageData;
    }

    @Data
    public static class WithdrawalPaymentProfile {
        private Long withdrawalId;
        private String methodType;
        private String payeeName;
        private String bankName;
        private String bankAccount;
        private String receiptImageData;
    }

    @Data
    public static class WithdrawalReceiptThumbnail {
        private Long withdrawalId;
        private String imageData;
    }

    @Data
    public static class ConfigView {
        private Integer enabled;
        private Integer commissionRateBps;
        private Long minWithdrawAmount;
        private String updateBy;
        private LocalDateTime updateTime;
    }

    @Data
    public static class BindResult {
        private Boolean bound;
        private Boolean alreadyBound;
        private String partnerNo;
    }

    @Data
    public static class Me {
        private Boolean partner;
        private Boolean entryVisible;
        private Long partnerId;
        private String partnerNo;
        private String status;
        private Boolean promotionEnabled;
    }

    @Data
    public static class Dashboard {
        private String partnerNo;
        private String status;
        private Long availableAmount;
        private Long totalCommissionAmount;
        private Long lockedAmount;
        private Long withdrawnAmount;
        private Long debtAmount;
        private Long visitCount;
        private Long promotedUserCount;
        private Long paidUserCount;
        private Long validOrderCount;
        private Long validOrderAmount;
        private Long minWithdrawAmount;
    }

    @Data
    public static class Qrcode {
        private String imageData;
        private String status;
        private Long visitCount;
        private Long promotedUserCount;
    }

    private DistributionViews() {
    }
}
