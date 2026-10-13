package com.aurora.admin.mobile.vo;

import com.aurora.framework.mobile.TokenPair;
import lombok.Data;

/**
 * 刷新令牌出参:仅返回新的双令牌
 */
@Data
public class TokenVO {

    /** 访问令牌 */
    private String token;

    /** 刷新令牌 */
    private String refreshToken;

    /**
     * 由令牌对组装出参
     */
    public static TokenVO of(TokenPair tokens) {
        TokenVO vo = new TokenVO();
        vo.setToken(tokens.getAccess());
        vo.setRefreshToken(tokens.getRefresh());
        return vo;
    }
}
