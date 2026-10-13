package com.aurora.test;

import com.aurora.common.core.redis.RedisCache;
import com.aurora.framework.config.RedisConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

import org.springframework.boot.autoconfigure.cache.CacheAutoConfiguration;

@SpringBootTest(classes = {RedisAutoConfiguration.class, CacheAutoConfiguration.class}, properties = "spring.main.allow-bean-definition-overriding=true")
@ContextConfiguration(classes = {RedisConfig.class, RedisCache.class})
public class RedisTest {

    @Autowired
    private RedisCache redisCache;

    @Test
    public void testRedisOperations() {
        System.out.println("Starting Redis Test in aurora-test module...");

        String key = "test:redis:integration:module";
        String value = "Hello Redis from aurora-test";

        // Test Set
        redisCache.setCacheObject(key, value);
        System.out.println("Set cache object: " + key + " -> " + value);

        // Test Get
        String retrievedValue = redisCache.getCacheObject(key);
        System.out.println("Get cache object: " + retrievedValue);
        assertEquals(value, retrievedValue, "Retrieved value should match set value");

        // Test Expire
        redisCache.expire(key, 10, TimeUnit.MINUTES);
        long expire = redisCache.getExpire(key);
        System.out.println("Expire time: " + expire);
        assertTrue(expire > 0, "Expire time should be set");

        // Test Delete
        redisCache.deleteObject(key);
        boolean hasKey = redisCache.hasKey(key);
        System.out.println("Has key after delete: " + hasKey);
        assertFalse(hasKey, "Key should be deleted");
        
        System.out.println("Redis Test Completed Successfully.");
    }
}
