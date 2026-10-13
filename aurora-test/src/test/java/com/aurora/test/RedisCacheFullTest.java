package com.aurora.test;

import com.aurora.common.core.redis.RedisCache;
import com.aurora.framework.config.RedisConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.cache.CacheAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Redis 缓存工具类完整单元测试
 * 测试 RedisCache 的所有方法功能
 */
@SpringBootTest(classes = { RedisAutoConfiguration.class,
        CacheAutoConfiguration.class }, properties = "spring.main.allow-bean-definition-overriding=true")
@ContextConfiguration(classes = { RedisConfig.class, RedisCache.class })
@DisplayName("Redis 缓存工具类测试")
public class RedisCacheFullTest {

    @Autowired
    private RedisCache redisCache;

    private static final String TEST_PREFIX = "test:unit:";

    // 用于记录测试创建的 key，测试后清理
    private final List<String> createdKeys = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        // 清理测试数据
        for (String key : createdKeys) {
            try {
                redisCache.deleteObject(key);
            } catch (Exception ignored) {
            }
        }
        createdKeys.clear();
    }

    private String testKey(String suffix) {
        String key = TEST_PREFIX + suffix + ":" + System.currentTimeMillis();
        createdKeys.add(key);
        return key;
    }

    // ==================== 基本对象操作测试 ====================
    @Nested
    @DisplayName("基本对象操作")
    class BasicObjectTest {

        @Test
        @DisplayName("测试设置和获取字符串")
        void testSetAndGetString() {
            String key = testKey("string");
            String value = "Hello Redis";

            redisCache.setCacheObject(key, value);
            String result = redisCache.getCacheObject(key);

            assertEquals(value, result);
            System.out.println("✅ 设置和获取字符串测试通过");
        }

        @Test
        @DisplayName("测试设置和获取整数")
        void testSetAndGetInteger() {
            String key = testKey("integer");
            Integer value = 12345;

            redisCache.setCacheObject(key, value);
            Integer result = redisCache.getCacheObject(key);

            assertEquals(value, result);
            System.out.println("✅ 设置和获取整数测试通过");
        }

        @Test
        @DisplayName("测试设置和获取对象")
        void testSetAndGetObject() {
            String key = testKey("object");
            Map<String, Object> value = new HashMap<>();
            value.put("name", "测试用户");
            value.put("age", 25);
            value.put("active", true);

            redisCache.setCacheObject(key, value);
            Map<String, Object> result = redisCache.getCacheObject(key);

            assertNotNull(result);
            assertEquals("测试用户", result.get("name"));
            assertEquals(25, result.get("age"));
            assertEquals(true, result.get("active"));
            System.out.println("✅ 设置和获取对象测试通过");
        }

        @Test
        @DisplayName("测试带过期时间的缓存")
        void testSetWithExpire() {
            String key = testKey("expire");
            String value = "Will Expire";

            redisCache.setCacheObject(key, value, 60, TimeUnit.SECONDS);

            assertTrue(redisCache.hasKey(key));
            long expire = redisCache.getExpire(key);
            assertTrue(expire > 0 && expire <= 60);
            System.out.println("✅ 带过期时间的缓存测试通过，剩余时间: " + expire + "s");
        }

        @Test
        @DisplayName("测试获取不存在的 key")
        void testGetNonExistentKey() {
            String key = "non:existent:key:" + System.currentTimeMillis();
            Object result = redisCache.getCacheObject(key);

            assertNull(result);
            System.out.println("✅ 获取不存在的 key 返回 null 测试通过");
        }
    }

    // ==================== Key 操作测试 ====================
    @Nested
    @DisplayName("Key 操作")
    class KeyOperationTest {

        @Test
        @DisplayName("测试判断 key 是否存在")
        void testHasKey() {
            String key = testKey("haskey");

            assertFalse(redisCache.hasKey(key));

            redisCache.setCacheObject(key, "value");
            assertTrue(redisCache.hasKey(key));
            System.out.println("✅ 判断 key 是否存在测试通过");
        }

        @Test
        @DisplayName("测试删除单个 key")
        void testDeleteSingleKey() {
            String key = testKey("delete");
            redisCache.setCacheObject(key, "to be deleted");

            assertTrue(redisCache.hasKey(key));

            boolean deleted = redisCache.deleteObject(key);
            assertTrue(deleted);
            assertFalse(redisCache.hasKey(key));
            System.out.println("✅ 删除单个 key 测试通过");
        }

        @Test
        @DisplayName("测试批量删除 key")
        void testDeleteMultipleKeys() {
            String key1 = testKey("batch1");
            String key2 = testKey("batch2");
            String key3 = testKey("batch3");

            redisCache.setCacheObject(key1, "v1");
            redisCache.setCacheObject(key2, "v2");
            redisCache.setCacheObject(key3, "v3");

            Collection<String> keys = Arrays.asList(key1, key2, key3);
            long deleted = redisCache.deleteObject(keys);

            assertEquals(3, deleted);
            assertFalse(redisCache.hasKey(key1));
            assertFalse(redisCache.hasKey(key2));
            assertFalse(redisCache.hasKey(key3));
            System.out.println("✅ 批量删除 key 测试通过，删除了 " + deleted + " 个");
        }

        @Test
        @DisplayName("测试设置和获取过期时间")
        void testExpire() {
            String key = testKey("expiretime");
            redisCache.setCacheObject(key, "value");

            // 设置10分钟过期
            boolean success = redisCache.expire(key, 600);
            assertTrue(success);

            long expire = redisCache.getExpire(key);
            assertTrue(expire > 590 && expire <= 600);
            System.out.println("✅ 设置和获取过期时间测试通过，过期时间: " + expire + "s");
        }

        @Test
        @DisplayName("测试 keys 模式匹配")
        void testKeys() {
            String prefix = TEST_PREFIX + "pattern:" + System.currentTimeMillis() + ":";
            String key1 = prefix + "a";
            String key2 = prefix + "b";
            String key3 = prefix + "c";
            createdKeys.add(key1);
            createdKeys.add(key2);
            createdKeys.add(key3);

            redisCache.setCacheObject(key1, "1");
            redisCache.setCacheObject(key2, "2");
            redisCache.setCacheObject(key3, "3");

            Collection<String> matchedKeys = redisCache.keys(prefix + "*");
            assertTrue(matchedKeys.size() >= 3);
            System.out.println("✅ keys 模式匹配测试通过，匹配到 " + matchedKeys.size() + " 个 key");
        }
    }

    // ==================== List 操作测试 ====================
    @Nested
    @DisplayName("List 操作")
    class ListOperationTest {

        @Test
        @DisplayName("测试 List 缓存设置和获取")
        void testSetAndGetList() {
            String key = testKey("list");
            List<String> list = Arrays.asList("item1", "item2", "item3");

            long count = redisCache.setCacheList(key, list);
            assertEquals(3, count);

            List<String> result = redisCache.getCacheList(key);
            assertEquals(3, result.size());
            assertEquals("item1", result.get(0));
            assertEquals("item2", result.get(1));
            assertEquals("item3", result.get(2));
            System.out.println("✅ List 缓存设置和获取测试通过");
        }

        @Test
        @DisplayName("测试空 List 缓存 - Redis 不支持空列表")
        void testEmptyList() {
            String key = testKey("emptylist");
            List<String> emptyList = Collections.emptyList();

            // Redis 不支持空列表操作，应抠异常
            assertThrows(IllegalArgumentException.class, () -> {
                redisCache.setCacheList(key, emptyList);
            });
            System.out.println("✅ 空 List 缓存测试通过（验证 Redis 不支持空列表）");
        }
    }

    // ==================== Set 操作测试 ====================
    @Nested
    @DisplayName("Set 操作")
    class SetOperationTest {

        @Test
        @DisplayName("测试 Set 缓存设置和获取")
        void testSetAndGetSet() {
            String key = testKey("set");
            Set<String> dataSet = new HashSet<>(Arrays.asList("a", "b", "c", "d"));

            redisCache.setCacheSet(key, dataSet);
            Set<String> result = redisCache.getCacheSet(key);

            assertEquals(4, result.size());
            assertTrue(result.contains("a"));
            assertTrue(result.contains("b"));
            assertTrue(result.contains("c"));
            assertTrue(result.contains("d"));
            System.out.println("✅ Set 缓存设置和获取测试通过");
        }

        @Test
        @DisplayName("测试 Set 去重特性")
        void testSetDeduplication() {
            String key = testKey("setdedup");
            Set<String> dataSet = new HashSet<>(Arrays.asList("x", "x", "y", "y", "z"));

            redisCache.setCacheSet(key, dataSet);
            Set<String> result = redisCache.getCacheSet(key);

            assertEquals(3, result.size()); // 去重后只有 3 个
            System.out.println("✅ Set 去重特性测试通过");
        }
    }

    // ==================== Map/Hash 操作测试 ====================
    @Nested
    @DisplayName("Map/Hash 操作")
    class MapOperationTest {

        @Test
        @DisplayName("测试 Map 缓存设置和获取")
        void testSetAndGetMap() {
            String key = testKey("map");
            Map<String, String> dataMap = new HashMap<>();
            dataMap.put("field1", "value1");
            dataMap.put("field2", "value2");
            dataMap.put("field3", "value3");

            redisCache.setCacheMap(key, dataMap);
            Map<String, String> result = redisCache.getCacheMap(key);

            assertEquals(3, result.size());
            assertEquals("value1", result.get("field1"));
            assertEquals("value2", result.get("field2"));
            assertEquals("value3", result.get("field3"));
            System.out.println("✅ Map 缓存设置和获取测试通过");
        }

        @Test
        @DisplayName("测试单个 Hash 字段操作")
        void testHashFieldOperations() {
            String key = testKey("hash");

            // 设置单个字段
            redisCache.setCacheMapValue(key, "name", "张三");
            redisCache.setCacheMapValue(key, "age", 30);
            redisCache.setCacheMapValue(key, "city", "北京");

            // 获取单个字段
            assertEquals("张三", redisCache.getCacheMapValue(key, "name"));
            assertEquals(Integer.valueOf(30), redisCache.getCacheMapValue(key, "age"));
            assertEquals("北京", redisCache.getCacheMapValue(key, "city"));

            // 删除字段
            redisCache.delCacheMapValue(key, "city");
            assertNull(redisCache.getCacheMapValue(key, "city"));

            System.out.println("✅ 单个 Hash 字段操作测试通过");
        }

        @Test
        @DisplayName("测试批量获取 Hash 字段")
        void testMultiGetHash() {
            String key = testKey("multihash");

            redisCache.setCacheMapValue(key, "k1", "v1");
            redisCache.setCacheMapValue(key, "k2", "v2");
            redisCache.setCacheMapValue(key, "k3", "v3");

            Collection<Object> hKeys = Arrays.asList("k1", "k2", "k3");
            List<String> values = redisCache.getMultiCacheMapValue(key, hKeys);

            assertEquals(3, values.size());
            assertTrue(values.contains("v1"));
            assertTrue(values.contains("v2"));
            assertTrue(values.contains("v3"));
            System.out.println("✅ 批量获取 Hash 字段测试通过");
        }

        @Test
        @DisplayName("测试 null Map 处理")
        void testNullMap() {
            String key = testKey("nullmap");

            // 设置 null 应该不会抛异常
            assertDoesNotThrow(() -> redisCache.setCacheMap(key, null));
            System.out.println("✅ null Map 处理测试通过");
        }
    }

    // ==================== 边界情况和并发测试 ====================
    @Nested
    @DisplayName("边界情况测试")
    class EdgeCaseTest {

        @Test
        @DisplayName("测试特殊字符 key")
        void testSpecialCharacterKey() {
            String key = testKey("special:chars:中文:emoji🔥");
            String value = "特殊字符测试";

            redisCache.setCacheObject(key, value);
            assertEquals(value, redisCache.getCacheObject(key));
            System.out.println("✅ 特殊字符 key 测试通过");
        }

        @Test
        @DisplayName("测试大对象缓存")
        void testLargeObject() {
            String key = testKey("large");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 10000; i++) {
                sb.append("测试数据_").append(i).append(";");
            }
            String largeValue = sb.toString();

            redisCache.setCacheObject(key, largeValue);
            String result = redisCache.getCacheObject(key);

            assertEquals(largeValue, result);
            System.out.println("✅ 大对象缓存测试通过，数据大小: " + largeValue.length() + " 字符");
        }

        @Test
        @DisplayName("测试覆盖已存在的 key")
        void testOverwriteKey() {
            String key = testKey("overwrite");

            redisCache.setCacheObject(key, "first");
            assertEquals("first", redisCache.getCacheObject(key));

            redisCache.setCacheObject(key, "second");
            assertEquals("second", redisCache.getCacheObject(key));
            System.out.println("✅ 覆盖已存在的 key 测试通过");
        }
    }

    // ==================== 综合测试 ====================
    @Test
    @DisplayName("综合测试 - 模拟真实业务场景")
    void testRealBusinessScenario() {
        // 模拟用户会话缓存
        String sessionKey = testKey("session:user:1001");
        Map<String, Object> sessionData = new HashMap<>();
        sessionData.put("userId", 1001);
        sessionData.put("userName", "admin");
        sessionData.put("loginTime", System.currentTimeMillis());
        sessionData.put("permissions", Arrays.asList("read", "write", "delete"));

        // 缓存会话，1小时过期
        redisCache.setCacheObject(sessionKey, sessionData, 3600, TimeUnit.SECONDS);

        // 验证会话存在
        assertTrue(redisCache.hasKey(sessionKey));

        // 获取会话数据
        Map<String, Object> retrieved = redisCache.getCacheObject(sessionKey);
        assertEquals(1001, retrieved.get("userId"));
        assertEquals("admin", retrieved.get("userName"));

        // 模拟用户权限缓存
        String permKey = testKey("perm:user:1001");
        Set<String> permissions = new HashSet<>(
                Arrays.asList("system:user:list", "system:user:add", "system:user:edit"));
        redisCache.setCacheSet(permKey, permissions);

        Set<String> cachedPerms = redisCache.getCacheSet(permKey);
        assertEquals(3, cachedPerms.size());
        assertTrue(cachedPerms.contains("system:user:list"));

        // 模拟配置缓存
        String configKey = testKey("config:system");
        redisCache.setCacheMapValue(configKey, "site.name", "极光AI平台");
        redisCache.setCacheMapValue(configKey, "site.version", "1.0.0");
        redisCache.setCacheMapValue(configKey, "site.copyright", "© 2024 Aurora");

        assertEquals("极光AI平台", redisCache.getCacheMapValue(configKey, "site.name"));

        System.out.println("==========================================");
        System.out.println("✅ 综合业务场景测试通过！");
        System.out.println("- 用户会话缓存: " + sessionKey);
        System.out.println("- 用户权限缓存: " + permKey);
        System.out.println("- 系统配置缓存: " + configKey);
        System.out.println("==========================================");
    }
}
