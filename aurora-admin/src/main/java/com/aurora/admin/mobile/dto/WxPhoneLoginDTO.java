package com.aurora.admin.mobile.dto;

import lombok.Data;

/**
 * 微信手机号一键登录入参
 * loginCode:wx.login 取的登录凭证;phoneCode:getPhoneNumber 取的手机号凭证。两者相互独立。
 */
@Data
public class WxPhoneLoginDTO {

    /** 登录凭证(换 openid/unionid) */
    private String loginCode;

    /** 手机号凭证(换手机号) */
    private String phoneCode;
}
