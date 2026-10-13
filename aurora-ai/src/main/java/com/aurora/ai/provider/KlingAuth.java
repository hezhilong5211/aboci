package com.aurora.ai.provider;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 可灵 Kling JWT 鉴权工具
 *
 * <p>可灵用 AccessKey + SecretKey 现场生成 JWT（HMAC-SHA256）做鉴权，不是固定 apiKey。
 * 这里用 JDK 原生 {@link javax.crypto.Mac} + Base64URL 实现，不引入 jjwt 等第三方库。</p>
 *
 * <p>Token 缓存 30 分钟，提前 60 秒刷新；{@link #getToken} 加锁保证并发安全。</p>
 */
public class KlingAuth {

    /** 全局单例，供 client 复用同一份 Token 缓存 */
    public static final KlingAuth INSTANCE = new KlingAuth();

    /** 当前缓存的 Token */
    private String cachedToken;

    /** 当前缓存 Token 的过期时刻（秒级时间戳） */
    private long expireAtSec;

    /**
     * 获取 JWT：优先用缓存，过期（提前 60 秒）则重新生成。
     *
     * @param accessKey 访问密钥（作为 JWT 的 iss）
     * @param secretKey 签名密钥（HMAC-SHA256 密钥）
     * @return 可直接作为 Bearer 的 JWT 字符串
     */
    public synchronized String getToken(String accessKey, String secretKey) {
        long now = System.currentTimeMillis() / 1000;
        // 提前 60 秒刷新，避免临界过期
        if (cachedToken != null && expireAtSec - 60 > now) {
            return cachedToken;
        }
        cachedToken = generate(accessKey, secretKey);
        expireAtSec = now + 1800; // 30 分钟有效
        return cachedToken;
    }

    /**
     * 生成 JWT：header.payload.signature，三段均为 Base64URL（无 padding）。
     */
    private String generate(String accessKey, String secretKey) {
        try {
            long now = System.currentTimeMillis() / 1000;
            String header = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
            // iss=AccessKey；exp 30 分钟后过期；nbf 提前 5 秒容忍时钟偏差
            String payload = "{\"iss\":\"" + accessKey + "\",\"exp\":" + (now + 1800) + ",\"nbf\":" + (now - 5) + "}";

            String h = b64url(header.getBytes(StandardCharsets.UTF_8));
            String p = b64url(payload.getBytes(StandardCharsets.UTF_8));
            String signingInput = h + "." + p;

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] sig = mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));

            return signingInput + "." + b64url(sig);
        } catch (Exception e) {
            throw new RuntimeException("可灵 JWT 生成失败", e);
        }
    }

    /** Base64URL 编码：无 padding，+/ 换成 -_，JDK 原生支持 */
    private String b64url(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }
}
