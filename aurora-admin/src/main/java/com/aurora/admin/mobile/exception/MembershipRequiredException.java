package com.aurora.admin.mobile.exception;

import com.aurora.common.core.exception.ServiceException;

/** 加油包下单或履约时会员资格已失效。 */
public class MembershipRequiredException extends ServiceException {

    public MembershipRequiredException(String message) {
        super(message);
    }
}
