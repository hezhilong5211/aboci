package com.aurora.framework.web.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.aurora.common.core.constant.CacheConstants;
import com.aurora.common.core.domain.model.LoginUser;
import com.aurora.common.core.redis.RedisCache;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * Token管理器
 */
@Slf4j
@Component
public class TokenService {

    // 令牌自定义标识
    @Value("${token.header:Authorization}")
    private String header;

    // 令牌秘钥
    @Value("${token.secret:abcdefghijklmnopqrstuvwxyz}")
    private String secret;

    // 令牌有效期（默认12小时，单位：分钟）
    @Value("${token.expireTime:720}")
    private int expireTime;

    protected static final long MILLIS_SECOND = 1000;
    protected static final long MILLIS_MINUTE = 60 * MILLIS_SECOND;
    private static final Long MILLIS_MINUTE_TEN = 20 * 60 * 1000L;

    @Autowired
    private RedisCache redisCache;

    /**
     * 获取用户身份信息
     */
    public LoginUser getLoginUser(HttpServletRequest request) {
        // 获取请求携带的令牌
        String token = getToken(request);
        if (StrUtil.isNotEmpty(token)) {
            try {
                Claims claims = parseToken(token);
                // 解析对应的权限以及用户信息
                String uuid = (String) claims.get("login_user_key");
                if (StrUtil.isEmpty(uuid)) {
                    return null;
                }
                // 从Redis获取用户信息
                String userKey = getTokenKey(uuid);
                LoginUser loginUser = redisCache.getCacheObject(userKey);
                if (loginUser != null) {
                    // 验证并刷新 token（如果接近过期）
                    verifyToken(loginUser);
                    return loginUser;
                }
                // 缓存中没有，但 JWT 是有效的，说明可能是服务重启了
                return null;
            } catch (io.jsonwebtoken.ExpiredJwtException e) {
                // JWT 已过期
                return null;
            } catch (Exception e) {
                // Token解析失败（签名错误、格式错误等）
                log.error("Token解析失败: {}", e.getMessage());
                return null;
            }
        }
        return null;
    }

    /**
     * 创建令牌
     */
    public String createToken(LoginUser loginUser) {
        String token = IdUtil.fastSimpleUUID();
        loginUser.setToken(token);

        // 刷新令牌有效期
        refreshToken(loginUser);

        Map<String, Object> claims = new HashMap<>();
        claims.put("login_user_key", token);
        return createToken(claims);
    }

    /**
     * 从数据声明生成令牌
     */
    private String createToken(Map<String, Object> claims) {
        SecretKey key = getSigningKey();
        long now = System.currentTimeMillis();
        // 显式签发时间与过期时间(硬上限),令牌不再永久有效;过期由 parseToken 的 ExpiredJwtException 拦截
        return Jwts.builder()
                .claims(claims)
                .issuedAt(new java.util.Date(now))
                .expiration(new java.util.Date(now + (long) expireTime * MILLIS_MINUTE))
                .signWith(key, Jwts.SIG.HS512)
                .compact();
    }

    /**
     * 从令牌中获取数据声明
     */
    private Claims parseToken(String token) {
        SecretKey key = getSigningKey();
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 启动即校验密钥强度:HS512 要求密钥至少 64 字节(512 位),
     * 不足直接 fail-fast,避免 jjwt 按密钥长度静默降级为 HS384/HS256。
     */
    @PostConstruct
    public void validateSecret() {
        int len = secret.getBytes(StandardCharsets.UTF_8).length;
        if (len < 64) {
            throw new IllegalStateException(
                    "token.secret 长度不足:HS512 要求密钥至少 64 字节,当前 " + len
                            + " 字节。请在 application-{profile}.yml 配置足够长度的密钥。");
        }
    }

    /**
     * 获取签名密钥(已由 validateSecret 保证 ≥64 字节,适配 HS512)
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 获取请求token
     */
    private String getToken(HttpServletRequest request) {
        String token = request.getHeader(header);
        if (StrUtil.isNotEmpty(token) && token.startsWith("Bearer ")) {
            token = token.replace("Bearer ", "");
        }
        return token;
    }

    private String getTokenKey(String uuid) {
        return CacheConstants.LOGIN_TOKEN_KEY + uuid;
    }

    /**
     * 删除用户身份信息
     */
    public void deleteLoginUser(String token) {
        if (StrUtil.isNotEmpty(token)) {
            String userKey = getTokenKey(token);
            redisCache.deleteObject(userKey);
        }
    }

    /**
     * 验证令牌有效期，相差不足20分钟，自动刷新缓存
     */
    public void verifyToken(LoginUser loginUser) {
        long expireTime = loginUser.getExpireTime();
        long currentTime = System.currentTimeMillis();
        if (expireTime - currentTime <= MILLIS_MINUTE_TEN) {
            refreshToken(loginUser);
        }
    }

    /**
     * 刷新令牌有效期
     * 
     * @param loginUser 登录信息
     */
    public void refreshToken(LoginUser loginUser) {
        loginUser.setLoginTime(System.currentTimeMillis());
        loginUser.setExpireTime(loginUser.getLoginTime() + expireTime * MILLIS_MINUTE);
        // 根据uuid将loginUser缓存
        String userKey = getTokenKey(loginUser.getToken());
        redisCache.setCacheObject(userKey, loginUser, expireTime, TimeUnit.MINUTES);
    }
}
