package com.aurora.admin.distribution.support;

/**
 * 一级分销固定业务常量。
 */
public final class DistributionConstants {

    public static final String PARTNER_ACTIVE = "ACTIVE";
    public static final String PARTNER_DISABLED = "DISABLED";

    public static final String COMMISSION_AVAILABLE = "AVAILABLE";
    public static final String COMMISSION_PARTIAL = "PARTIAL";
    public static final String COMMISSION_LOCKED = "LOCKED";
    public static final String COMMISSION_WITHDRAWN = "WITHDRAWN";
    public static final String COMMISSION_REVERSED = "REVERSED";

    public static final String WITHDRAW_SUBMITTED = "SUBMITTED";
    public static final String WITHDRAW_NEEDS_REVIEW = "NEEDS_REVIEW";
    public static final String WITHDRAW_PAID = "PAID";
    public static final String WITHDRAW_REJECTED = "REJECTED";

    public static final String ALLOCATION_LOCKED = "LOCKED";
    public static final String ALLOCATION_RESTORED = "RESTORED";
    public static final String ALLOCATION_PAID = "PAID";
    public static final String ALLOCATION_REVERSED = "REVERSED";

    public static final String METHOD_WECHAT_QR = "WECHAT_QR";
    public static final String METHOD_ALIPAY_QR = "ALIPAY_QR";
    public static final String METHOD_BANK_CARD = "BANK_CARD";

    public static final String TX_COMMISSION = "COMMISSION";
    public static final String TX_DEBT_OFFSET = "DEBT_OFFSET";
    public static final String TX_WITHDRAW_LOCK = "WITHDRAW_LOCK";
    public static final String TX_WITHDRAW_RESTORE = "WITHDRAW_RESTORE";
    public static final String TX_WITHDRAW_PAID = "WITHDRAW_PAID";
    public static final String TX_REFUND_REVERSE = "REFUND_REVERSE";
    public static final String TX_ADJUST = "ADJUST";

    public static final long CONFIG_ID = 1L;

    private DistributionConstants() {
    }
}
