package com.aurora.admin.mobile.vo;

import com.aurora.admin.mobile.domain.MobileUser;
import com.aurora.framework.mobile.TokenPair;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 登录出参:双令牌 + 是否新用户 + 用户信息
 */
@Data
public class LoginVO {

    /** 访问令牌 */
    private String token;

    /** 刷新令牌 */
    private String refreshToken;

    /** 是否新注册用户(显式固定 JSON 字段名为 isNewUser,对齐前端契约) */
    @JsonProperty("isNewUser")
    private boolean isNewUser;

    /** 用户信息 */
    private UserInfoVO userInfo;

    /**
     * 由令牌对、是否新用户、用户实体组装出参
     */
    public static LoginVO of(TokenPair tokens, boolean isNewUser, MobileUser user) {
        LoginVO vo = new LoginVO();
        vo.setToken(tokens.getAccess());
        vo.setRefreshToken(tokens.getRefresh());
        vo.setNewUser(isNewUser);
        vo.setUserInfo(UserInfoVO.from(user));
        return vo;
    }
}
