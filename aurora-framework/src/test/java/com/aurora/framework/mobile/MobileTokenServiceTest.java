package com.aurora.framework.mobile;

import com.aurora.common.core.exception.ServiceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MobileTokenServiceTest {
    private static final String SECRET = "12345678901234567890123456789012";

    @Test
    void shouldRejectMissingOrShortSecretAtStartup() throws Exception {
        assertThrows(IllegalStateException.class, service(null, "")::validateSecret);
        assertThrows(IllegalStateException.class, service(null, "too-short")::validateSecret);
    }

    @Test
    void shouldAcceptSecretOfAtLeastThirtyTwoBytes() throws Exception {
        assertDoesNotThrow(service(null, SECRET)::validateSecret);
    }

    @Test
    void shouldIssueDistinctTokensEvenWithinOneSecond() throws Exception {
        MobileTokenService service = service(null, SECRET);
        HashSet<String> tokens = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            TokenPair pair = service.issue(1L, "test");
            assertTrue(tokens.add(pair.getAccess()));
            assertTrue(tokens.add(pair.getRefresh()));
        }
    }

    @Test
    void shouldRejectInvalidExpiredAndAccessTokensWithoutTouchingRedis() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        MobileTokenService service = service(redis, SECRET);
        for (String token : new String[]{"invalid", refreshExpiringAt(System.currentTimeMillis() - 2000), service.issue(1L, "test").getAccess()}) {
            assertEquals(401, assertThrows(ServiceException.class, () -> service.refresh(token)).getCode());
        }
        verifyNoInteractions(redis);
    }

    @Test
    void shouldReturnTheAtomicRotationResultAndPreserveUserIdentity() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        MobileTokenService service = service(redis, SECRET);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenAnswer(call -> call.getArgument(2, String.class));
        TokenPair original = service.issue(7L, "test");
        TokenPair rotated = service.refresh(original.getRefresh());
        assertNotEquals(original.getRefresh(), rotated.getRefresh());
        assertEquals(7L, service.getUserId(rotated.getAccess()));
    }

    @Test
    void shouldReturnCachedPairInsteadOfIssuingAnotherUsablePairOnRetry() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        MobileTokenService service = service(redis, SECRET);
        TokenPair cached = service.issue(7L, "test");
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(new ObjectMapper().writeValueAsString(cached));
        String original = service.issue(7L, "test").getRefresh();
        assertEquals(cached, service.refresh(original));
        assertEquals(cached, service.refresh(original));
    }

    @Test
    void shouldLimitReplayToSixtySecondsAndNeverExceedOriginalExpiry() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        MobileTokenService service = service(redis, SECRET);
        AtomicReference<Long> ttl = new AtomicReference<>();
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenAnswer(call -> {
            long remaining = Long.parseLong(call.getArgument(3));
            ttl.set(Long.parseLong(call.getArgument(4)));
            assertTrue(ttl.get() <= remaining);
            return call.getArgument(2, String.class);
        });
        service.refresh(service.issue(1L, "test").getRefresh());
        assertEquals(60_000L, ttl.get());
        service.refresh(refreshExpiringAt(System.currentTimeMillis() + 10_000));
        assertTrue(ttl.get() > 0 && ttl.get() <= 10_000);
    }

    @Test
    void shouldRejectConsumedTokenOutsideRetryWindow() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        MobileTokenService service = service(redis, SECRET);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn("");
        assertEquals(401, assertThrows(ServiceException.class,
                () -> service.refresh(service.issue(1L, "test").getRefresh())).getCode());
    }

    @Test
    void shouldNotReturnCachedSuccessorThatHasAlreadyBeenRevoked() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        MobileTokenService service = service(redis, SECRET);
        TokenPair cached = service.issue(1L, "test");
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(new ObjectMapper().writeValueAsString(cached));
        when(redis.hasKey("mobile_token_blacklist:" + cached.getRefresh())).thenReturn(true);
        assertEquals(401, assertThrows(ServiceException.class,
                () -> service.refresh(service.issue(1L, "test").getRefresh())).getCode());
    }

    @Test
    void shouldKeepRedisOutageDistinctFromInvalidCredentials() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        MobileTokenService service = service(redis, SECRET);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new RedisConnectionFailureException("unavailable"));
        assertThrows(RedisConnectionFailureException.class,
                () -> service.refresh(service.issue(1L, "test").getRefresh()));
    }

    @Test
    void shouldReportMissingOrCorruptRotationResultAsTemporaryFailure() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        MobileTokenService service = service(redis, SECRET);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(null, "not-json", "null", "{}");
        String original = service.issue(1L, "test").getRefresh();
        for (int i = 0; i < 4; i++) {
            assertEquals(503, assertThrows(ServiceException.class, () -> service.refresh(original)).getCode());
        }
    }

    private String refreshExpiringAt(long expiry) {
        return Jwts.builder().claim("user_id", 1L).claim("user_name", "test").claim("type", "refresh")
                .issuedAt(new Date(System.currentTimeMillis() - 5000)).expiration(new Date(expiry))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256).compact();
    }

    private MobileTokenService service(StringRedisTemplate redis, String secret) throws Exception {
        MobileTokenService service = new MobileTokenService(redis);
        Field field = MobileTokenService.class.getDeclaredField("secret");
        field.setAccessible(true);
        field.set(service, secret);
        return service;
    }
}
