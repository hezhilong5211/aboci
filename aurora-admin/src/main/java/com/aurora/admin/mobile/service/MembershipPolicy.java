package com.aurora.admin.mobile.service;

import java.time.LocalDateTime;
import java.time.ZoneId;

/** 会员业务公共规则。 */
public final class MembershipPolicy {

    /** 新建会员等级的默认有效天数。 */
    public static final int DURATION_DAYS = 365;

    /** 会员等级允许配置的最短有效天数。 */
    public static final int MIN_DURATION_DAYS = 1;

    /** 会员等级允许配置的最长有效天数。 */
    public static final int MAX_DURATION_DAYS = 3650;

    /** 判断有效天数是否处于管理后台允许配置的范围。 */
    public static boolean isValidDurationDays(Integer durationDays) {
        return durationDays != null
                && durationDays >= MIN_DURATION_DAYS
                && durationDays <= MAX_DURATION_DAYS;
    }

    /** 会员、支付和微信时间戳统一使用北京时间，避免 JVM 默认时区改变业务边界。 */
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    public static LocalDateTime now() {
        return LocalDateTime.now(BUSINESS_ZONE);
    }

    private MembershipPolicy() {
    }
}
