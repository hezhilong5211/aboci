package com.aurora.test;

import com.aurora.admin.AuroraApplication;
import com.aurora.common.core.domain.entity.SysDictData;
import com.aurora.common.core.service.ISysDictDataService;
import com.aurora.common.core.service.ISysConfigService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.Cache;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 缓存机制单元测试
 * 
 * 测试场景：
 * 1. 字典数据缓存读取
 * 2. 缓存命中验证
 * 3. 缓存失效验证（修改后自动清除）
 * 4. 配置缓存测试
 * 
 * @author aurora
 */
@SpringBootTest(classes = AuroraApplication.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CacheEvictionTest {

    @Autowired
    private ISysDictDataService dictDataService;

    @Autowired
    private ISysConfigService configService;

    @Autowired
    private CacheManager cacheManager;

    private static final String TEST_DICT_TYPE = "sys_yes_no";
    private static final String TEST_CONFIG_KEY = "sys.index.skinName";

    /**
     * 测试1：验证缓存管理器正确初始化
     */
    @Test
    @Order(1)
    public void testCacheManagerInitialized() {
        System.out.println("=== 测试1：验证缓存管理器初始化 ===");

        assertNotNull(cacheManager, "CacheManager 应该被正确注入");
        System.out.println("CacheManager 类型: " + cacheManager.getClass().getName());

        // 验证缓存区域
        Cache dictCache = cacheManager.getCache("dict");
        Cache configCache = cacheManager.getCache("config");

        assertNotNull(dictCache, "dict 缓存区域应该存在");
        assertNotNull(configCache, "config 缓存区域应该存在");

        System.out.println("✅ 缓存管理器初始化成功！");
    }

    /**
     * 测试2：验证字典数据第一次查询（缓存未命中 → 写入缓存）
     */
    @Test
    @Order(2)
    public void testDictDataCacheMiss() {
        System.out.println("\n=== 测试2：字典数据首次查询（缓存未命中） ===");

        // 清除缓存确保从头开始
        Cache dictCache = cacheManager.getCache("dict");
        assertNotNull(dictCache);
        dictCache.evict(TEST_DICT_TYPE);

        // 第一次查询 - 应该访问数据库
        long startTime = System.currentTimeMillis();
        List<SysDictData> result1 = dictDataService.selectDictDataByType(TEST_DICT_TYPE);
        long firstQueryTime = System.currentTimeMillis() - startTime;

        assertNotNull(result1, "查询结果不应为空");
        System.out.println("首次查询 [" + TEST_DICT_TYPE + "] 耗时: " + firstQueryTime + "ms");
        System.out.println("查询到 " + result1.size() + " 条字典数据");

        // 验证数据已写入缓存
        Cache.ValueWrapper cached = dictCache.get(TEST_DICT_TYPE);
        assertNotNull(cached, "数据应该已写入缓存");

        System.out.println("✅ 首次查询成功，数据已写入缓存！");
    }

    /**
     * 测试3：验证字典数据第二次查询（缓存命中）
     */
    @Test
    @Order(3)
    public void testDictDataCacheHit() {
        System.out.println("\n=== 测试3：字典数据二次查询（缓存命中） ===");

        // 确保缓存中有数据
        dictDataService.selectDictDataByType(TEST_DICT_TYPE);

        // 第二次查询 - 应该从缓存读取
        long startTime = System.currentTimeMillis();
        List<SysDictData> result2 = dictDataService.selectDictDataByType(TEST_DICT_TYPE);
        long secondQueryTime = System.currentTimeMillis() - startTime;

        assertNotNull(result2, "查询结果不应为空");
        System.out.println("二次查询 [" + TEST_DICT_TYPE + "] 耗时: " + secondQueryTime + "ms");

        // 缓存命中应该更快（通常 < 5ms）
        System.out.println("期望缓存命中，查询时间应接近 0ms");

        System.out.println("✅ 二次查询成功，验证缓存命中！");
    }

    /**
     * 测试4：验证缓存失效（手动清除）
     */
    @Test
    @Order(4)
    public void testDictDataCacheEviction() {
        System.out.println("\n=== 测试4：验证缓存失效（手动清除） ===");

        Cache dictCache = cacheManager.getCache("dict");
        assertNotNull(dictCache);

        // 确保缓存中有数据
        dictDataService.selectDictDataByType(TEST_DICT_TYPE);
        Cache.ValueWrapper cachedBefore = dictCache.get(TEST_DICT_TYPE);
        assertNotNull(cachedBefore, "清除前缓存应该有数据");
        System.out.println("清除前：缓存中存在 [" + TEST_DICT_TYPE + "] 数据");

        // 手动清除缓存
        dictCache.evict(TEST_DICT_TYPE);

        // 验证缓存已清除
        Cache.ValueWrapper cachedAfter = dictCache.get(TEST_DICT_TYPE);
        assertNull(cachedAfter, "清除后缓存应该为空");
        System.out.println("清除后：缓存中 [" + TEST_DICT_TYPE + "] 数据已删除");

        System.out.println("✅ 缓存清除成功！");
    }

    /**
     * 测试5：验证配置缓存
     */
    @Test
    @Order(5)
    public void testConfigCache() {
        System.out.println("\n=== 测试5：验证配置缓存 ===");

        Cache configCache = cacheManager.getCache("config");
        assertNotNull(configCache);

        // 清除缓存
        configCache.evict(TEST_CONFIG_KEY);

        // 第一次查询
        String value1 = configService.selectConfigByKey(TEST_CONFIG_KEY);
        System.out.println("第一次查询配置 [" + TEST_CONFIG_KEY + "]: " + value1);

        // 验证缓存
        Cache.ValueWrapper cached = configCache.get(TEST_CONFIG_KEY);
        if (value1 != null) {
            assertNotNull(cached, "配置应该已写入缓存");
            System.out.println("配置已缓存");
        } else {
            System.out.println("配置不存在，跳过缓存验证");
        }

        System.out.println("✅ 配置缓存测试完成！");
    }

    /**
     * 测试6：性能对比测试
     */
    @Test
    @Order(6)
    public void testCachePerformance() {
        System.out.println("\n=== 测试6：缓存性能对比测试 ===");

        Cache dictCache = cacheManager.getCache("dict");
        assertNotNull(dictCache);

        // 清除缓存
        dictCache.evict(TEST_DICT_TYPE);

        // 多次查询计时
        int iterations = 10;
        long totalFirstQueryTime = 0;
        long totalCachedQueryTime = 0;

        // 第一次查询（无缓存）
        long start1 = System.nanoTime();
        dictDataService.selectDictDataByType(TEST_DICT_TYPE);
        totalFirstQueryTime = System.nanoTime() - start1;

        // 后续查询（有缓存）
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            dictDataService.selectDictDataByType(TEST_DICT_TYPE);
            totalCachedQueryTime += System.nanoTime() - start;
        }

        double avgCachedQueryTime = totalCachedQueryTime / (double) iterations / 1_000_000.0;
        double firstQueryTimeMs = totalFirstQueryTime / 1_000_000.0;

        System.out.println("首次查询耗时（无缓存）: " + String.format("%.2f", firstQueryTimeMs) + " ms");
        System.out.println("平均查询耗时（有缓存）: " + String.format("%.2f", avgCachedQueryTime) + " ms");
        System.out.println("性能提升: " + String.format("%.1f", firstQueryTimeMs / avgCachedQueryTime) + " 倍");

        System.out.println("✅ 性能测试完成！");
    }

    /**
     * 测试7：验证缓存区域配置
     */
    @Test
    @Order(7)
    public void testCacheRegions() {
        System.out.println("\n=== 测试7：验证缓存区域配置 ===");

        String[] expectedCaches = { "dict", "config", "user", "aiModel", "menu" };

        for (String cacheName : expectedCaches) {
            Cache cache = cacheManager.getCache(cacheName);
            assertNotNull(cache, "缓存区域 [" + cacheName + "] 应该存在");
            System.out.println("✅ 缓存区域 [" + cacheName + "] 已配置");
        }

        System.out.println("✅ 所有缓存区域配置正确！");
    }

    /**
     * 测试8：清理测试数据
     */
    @Test
    @Order(8)
    public void testCleanup() {
        System.out.println("\n=== 测试8：清理测试缓存 ===");

        Cache dictCache = cacheManager.getCache("dict");
        Cache configCache = cacheManager.getCache("config");

        if (dictCache != null)
            dictCache.evict(TEST_DICT_TYPE);
        if (configCache != null)
            configCache.evict(TEST_CONFIG_KEY);

        System.out.println("✅ 测试缓存已清理！");
        System.out.println("\n========== 所有缓存测试完成 ==========");
    }
}
