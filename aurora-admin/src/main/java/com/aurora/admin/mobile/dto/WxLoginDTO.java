package com.aurora.admin.mobile.dto;

import lombok.Data;

/**
 * 微信登录/补绑手机号入参
 * wx/login 时 code 为登录凭证;wx/bindPhone 时 code 为手机号凭证。
 */
@Data
public class WxLoginDTO {

    /** 微信凭证 */
    private String code;
}
