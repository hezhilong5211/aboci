package com.aurora.common.core.constant;

/**
 * 缓存(Redis)Key 前缀常量:收口散落在各服务中的硬编码前缀。
 *
 * <p>地基件,接入由后续阶段逐步替换 TokenService / SysLoginService / RateLimiterAspect 等处的字面量。
 *
 * @author aurora
 */
public final class CacheConstants {

    private CacheConstants() {
    }

    /** 登录用户会话:login_tokens:{uuid} */
    public static final String LOGIN_TOKEN_KEY = "login_tokens:";

    /** 图片验证码:captcha_codes:{uuid} */
    public static final String CAPTCHA_CODE_KEY = "captcha_codes:";

    /** 账号密码错误计数:pwd_err_cnt:{username} */
    public static final String PWD_ERR_CNT_KEY = "pwd_err_cnt:";

    /** 接口限流计数:rate_limit:{标识} */
    public static final String RATE_LIMIT_KEY = "rate_limit:";
}
