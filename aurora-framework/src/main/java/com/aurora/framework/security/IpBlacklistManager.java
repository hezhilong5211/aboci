package com.aurora.framework.security;

import com.aurora.common.core.redis.RedisCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * IP 黑名单管理器
 * 自动封锁触发限流过多的IP
 *
 * @author aurora
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IpBlacklistManager {

    private final RedisCache redisCache;

    /**
     * 黑名单 Redis Key 前缀
     */
    private static final String BLACKLIST_PREFIX = "ip:blacklist:";

    /**
     * 限流触发次数 Redis Key 前缀
     */
    private static final String TRIGGER_COUNT_PREFIX = "ip:trigger:";

    /**
     * 白名单 Redis Key 前缀（永不封锁）
     */
    private static final String WHITELIST_PREFIX = "ip:whitelist:";

    /**
     * 触发封锁阈值（1分钟内触发限流超过此次数则封锁）
     */
    private static final int BLOCK_THRESHOLD = 10;

    /**
     * 封锁时长（秒），默认30分钟
     */
    private static final int BLOCK_DURATION = 30 * 60;

    /**
     * 触发计数过期时间（秒）
     */
    private static final int TRIGGER_EXPIRE = 60;

    /**
     * 检查IP是否被封锁
     *
     * @param ip IP地址
     * @return true=被封锁
     */
    public boolean isBlocked(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }

        // 白名单直接放行
        if (isWhitelisted(ip)) {
            return false;
        }

        Boolean blocked = redisCache.getCacheObject(BLACKLIST_PREFIX + ip);
        return Boolean.TRUE.equals(blocked);
    }

    /**
     * 记录限流触发并检查是否需要封锁
     *
     * @param ip IP地址
     * @return true=已触发封锁
     */
    public boolean recordTriggerAndCheck(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }

        // 白名单不处理
        if (isWhitelisted(ip)) {
            return false;
        }

        String key = TRIGGER_COUNT_PREFIX + ip;
        // 原子自增,仅首次创建时设过期(避免读-改-写竞态)
        Long count = redisCache.increment(key);
        if (count != null && count == 1L) {
            redisCache.expire(key, TRIGGER_EXPIRE);
        }

        // 检查是否需要封锁
        if (count != null && count >= BLOCK_THRESHOLD) {
            blockIp(ip);
            log.warn("IP {} 触发限流 {} 次，已自动封锁 {} 分钟", ip, count, BLOCK_DURATION / 60);
            return true;
        }

        return false;
    }

    /**
     * 封锁IP
     */
    public void blockIp(String ip) {
        redisCache.setCacheObject(BLACKLIST_PREFIX + ip, true, BLOCK_DURATION, TimeUnit.SECONDS);
        // 清除触发计数
        redisCache.deleteObject(TRIGGER_COUNT_PREFIX + ip);
    }

    /**
     * 手动解封IP
     */
    public void unblockIp(String ip) {
        redisCache.deleteObject(BLACKLIST_PREFIX + ip);
        log.info("IP {} 已手动解封", ip);
    }

    /**
     * 添加到白名单（永不封锁）
     */
    public void addToWhitelist(String ip) {
        redisCache.setCacheObject(WHITELIST_PREFIX + ip, true);
        log.info("IP {} 已加入白名单", ip);
    }

    /**
     * 从白名单移除
     */
    public void removeFromWhitelist(String ip) {
        redisCache.deleteObject(WHITELIST_PREFIX + ip);
    }

    /**
     * 检查是否在白名单
     */
    public boolean isWhitelisted(String ip) {
        Boolean whitelisted = redisCache.getCacheObject(WHITELIST_PREFIX + ip);
        return Boolean.TRUE.equals(whitelisted);
    }

    /**
     * 获取IP剩余封锁时间（秒）
     */
    public long getBlockTTL(String ip) {
        // 这个需要 RedisCache 支持获取 TTL，暂时返回 -1
        return isBlocked(ip) ? BLOCK_DURATION : 0;
    }
}
