package com.aurora.admin.mobile.dto;

import lombok.Data;

/**
 * 刷新令牌入参
 */
@Data
public class RefreshTokenDTO {

    /** 刷新令牌 */
    private String refreshToken;
}
