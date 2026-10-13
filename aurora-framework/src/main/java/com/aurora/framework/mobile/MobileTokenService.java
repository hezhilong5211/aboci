package com.aurora.framework.mobile;

import com.aurora.common.core.exception.ServiceException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 移动端令牌服务
 * 签发/校验双令牌(access + refresh),HS256;黑名单基于 Redis。
 * access claim:user_id/user_name/type=mobile;refresh claim:user_id/type=refresh。
 */
@Service
@RequiredArgsConstructor
public class MobileTokenService {

    /** 令牌密钥 */
    @Value("${mobile.token.secret}")
    private String secret;

    /** access 令牌有效期:2 小时(毫秒) */
    private static final long ACCESS_EXPIRE_MILLIS = 2 * 60 * 60 * 1000L;

    /** refresh 令牌有效期:30 天(毫秒) */
    private static final long REFRESH_EXPIRE_MILLIS = 30L * 24 * 60 * 60 * 1000L;

    /** 令牌黑名单 Redis key 前缀 */
    private static final String BLACKLIST_KEY_PREFIX = "mobile_token_blacklist:";

    /** 响应丢失或并发重试时，60 秒内返回同一结果，不延长旧令牌有效期。 */
    private static final long REFRESH_RETRY_MILLIS = 60_000L;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final DefaultRedisScript<String> ROTATE_REFRESH = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 1 then
                return redis.call('GET', KEYS[2]) or ''
            end
            redis.call('SET', KEYS[2], ARGV[1], 'PX', ARGV[3])
            redis.call('SET', KEYS[1], '1', 'PX', ARGV[2])
            return ARGV[1]
            """, String.class);

    private final StringRedisTemplate redisTemplate;

    /** 启动即校验生产配置，避免空密钥或短密钥签发可伪造令牌。 */
    @PostConstruct
    void validateSecret() {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("mobile.token.secret 必须配置且不少于 32 字节");
        }
    }

    /**
     * 签发双令牌
     *
     * @param userId   用户ID
     * @param userName 用户名
     * @return access + refresh 令牌对
     */
    public TokenPair issue(Long userId, String userName) {
        SecretKey key = getSigningKey();
        long now = System.currentTimeMillis();

        String access = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .claim("user_id", userId)
                .claim("user_name", userName)
                .claim("type", "mobile")
                .issuedAt(new Date(now))
                .expiration(new Date(now + ACCESS_EXPIRE_MILLIS))
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        String refresh = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .claim("user_id", userId)
                .claim("user_name", userName)
                .claim("type", "refresh")
                .issuedAt(new Date(now))
                .expiration(new Date(now + REFRESH_EXPIRE_MILLIS))
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        return new TokenPair(access, refresh);
    }

    /**
     * 校验 access 令牌并返回用户ID
     * 先查黑名单,再验签;校验失败抛 ServiceException。
     *
     * @param token access 令牌
     * @return 用户ID
     */
    public Long getUserId(String token) {
        if (isBlacklisted(token)) {
            throw new ServiceException("令牌已失效，请重新登录");
        }
        try {
            Claims claims = parseClaims(token);
            if (!"mobile".equals(claims.get("type", String.class))) {
                throw new ServiceException("令牌类型不正确");
            }
            return claims.get("user_id", Number.class).longValue();
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("令牌无效或已过期，请重新登录");
        }
    }

    /**
     * 刷新令牌:原子轮换并短暂保存结果，网络重试取回同一双令牌。
     *
     * @param refreshToken 旧的 refresh 令牌
     * @return 新的 access + refresh 令牌对
     */
    public TokenPair refresh(String refreshToken) {
        Claims claims;
        try {
            claims = parseClaims(refreshToken);
        } catch (Exception e) {
            throw new ServiceException("刷新令牌无效或已过期，请重新登录", 401);
        }
        if (!"refresh".equals(claims.get("type", String.class))) {
            throw new ServiceException("令牌类型不正确", 401);
        }
        Long userId = claims.get("user_id", Number.class).longValue();
        String userName = claims.get("user_name", String.class);

        long remaining = claims.getExpiration().getTime() - System.currentTimeMillis();
        if (remaining <= 0) {
            throw new ServiceException("刷新令牌无效或已过期，请重新登录", 401);
        }
        try {
            String result = redisTemplate.execute(ROTATE_REFRESH,
                    List.of(BLACKLIST_KEY_PREFIX + refreshToken, "mobile_token_refresh_result:" + refreshToken),
                    JSON.writeValueAsString(issue(userId, userName)),
                    Long.toString(remaining), Long.toString(Math.min(REFRESH_RETRY_MILLIS, remaining)));
            if (result == null) {
                throw new ServiceException("登录续期暂时不可用，请稍后重试", 503);
            }
            if (result.isEmpty()) {
                throw new ServiceException("刷新令牌已失效，请重新登录", 401);
            }
            TokenPair pair = JSON.readValue(result, TokenPair.class);
            if (pair == null || pair.getAccess() == null || pair.getAccess().isBlank()
                    || pair.getRefresh() == null || pair.getRefresh().isBlank()) {
                throw new ServiceException("登录续期暂时不可用，请稍后重试", 503);
            }
            // 后继 refresh 已轮换或撤销时，旧令牌不能借重试窗口取回失效结果。
            if (isBlacklisted(pair.getRefresh())) {
                throw new ServiceException("刷新令牌已失效，请重新登录", 401);
            }
            return pair;
        } catch (JsonProcessingException e) {
            throw new ServiceException("登录续期暂时不可用，请稍后重试", 503);
        }
    }

    /**
     * 将令牌加入黑名单
     * TTL 取令牌剩余有效期;令牌已过期或无法解析则不入黑名单(本身已失效)。
     *
     * @param token 令牌
     */
    public void blacklist(String token) {
        long expireMillis;
        try {
            Claims claims = parseClaims(token);
            expireMillis = claims.getExpiration().getTime() - System.currentTimeMillis();
        } catch (Exception e) {
            // 令牌已过期或无效,无需拉黑
            return;
        }
        if (expireMillis <= 0) {
            return;
        }
        redisTemplate.opsForValue().set(BLACKLIST_KEY_PREFIX + token, "1", expireMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * 判断令牌是否在黑名单中
     */
    private boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_KEY_PREFIX + token));
    }

    /**
     * 解析并验签令牌,返回声明
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 获取签名密钥
     */
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
