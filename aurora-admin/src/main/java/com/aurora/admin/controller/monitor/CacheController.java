package com.aurora.admin.controller.monitor;

import com.aurora.common.core.domain.Result;
import com.aurora.common.core.redis.RedisCache;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 缓存监控控制器
 * 
 * @author aurora
 */
@Tag(name = "缓存监控", description = "Redis缓存的查看和管理接口")
@RestController
@RequestMapping("/monitor/cache")
public class CacheController {

    private final RedisCache redisCache;

    /**
     * 缓存名称与备注的映射关系
     */
    private static final Map<String, String> CACHE_NAMES = new LinkedHashMap<>();

    static {
        CACHE_NAMES.put("login_tokens", "用户登录令牌");
        CACHE_NAMES.put("sys_config", "系统配置");
        CACHE_NAMES.put("sys_dict", "数据字典");
        CACHE_NAMES.put("captcha_codes", "验证码");
        CACHE_NAMES.put("repeat_submit", "防重复提交");
        CACHE_NAMES.put("rate_limit", "限流处理");
        CACHE_NAMES.put("pwd_err_cnt", "密码错误次数");
    }

    public CacheController(RedisCache redisCache) {
        this.redisCache = redisCache;
    }

    /**
     * 获取缓存名称列表
     *
     * @return 缓存名称列表
     */
    @Operation(summary = "获取缓存名称列表", description = "获取系统中所有缓存分类的名称列表")
    @GetMapping("/names")
    @PreAuthorize("@ss.hasPermi('monitor:cache:list')")
    public Result<List<CacheNameVO>> getCacheNames() {
        List<CacheNameVO> list = new ArrayList<>();
        for (Map.Entry<String, String> entry : CACHE_NAMES.entrySet()) {
            CacheNameVO vo = new CacheNameVO();
            vo.setCacheName(entry.getKey());
            vo.setRemark(entry.getValue());
            list.add(vo);
        }
        return Result.success(list);
    }

    /**
     * 获取缓存键名列表
     *
     * @param cacheName 缓存名称
     * @return 缓存键名列表
     */
    @Operation(summary = "获取缓存键名列表", description = "根据缓存名称获取该分类下的所有键名")
    @GetMapping("/keys/{cacheName}")
    @PreAuthorize("@ss.hasPermi('monitor:cache:list')")
    public Result<List<CacheKeyVO>> getCacheKeys(
            @Parameter(description = "缓存名称") @PathVariable String cacheName) {

        // 使用通配符查询该缓存名称下的所有键
        Collection<String> keys = redisCache.keys(cacheName + ":*");
        if (keys == null || keys.isEmpty()) {
            // 如果没有找到带冒号的键，尝试直接查询该键名
            keys = redisCache.keys(cacheName);
        }

        List<CacheKeyVO> list = new ArrayList<>();
        if (keys != null) {
            int index = 1;
            for (String key : keys) {
                CacheKeyVO vo = new CacheKeyVO();
                vo.setCacheKey(key);
                vo.setRemark("键 " + index++);
                list.add(vo);
            }
        }
        return Result.success(list);
    }

    /**
     * 获取缓存内容
     *
     * @param cacheName 缓存名称
     * @param cacheKey  缓存键名
     * @return 缓存内容
     */
    @Operation(summary = "获取缓存内容", description = "根据缓存名称和键名获取缓存的具体内容")
    @GetMapping("/value")
    @PreAuthorize("@ss.hasPermi('monitor:cache:list')")
    public Result<CacheContentVO> getCacheValue(
            @Parameter(description = "缓存名称") @RequestParam String cacheName,
            @Parameter(description = "缓存键名") @RequestParam String cacheKey) {

        Object cacheValue = redisCache.getCacheObject(cacheKey);

        CacheContentVO vo = new CacheContentVO();
        vo.setCacheName(cacheName);
        vo.setCacheKey(cacheKey);
        vo.setCacheValue(cacheValue != null ? cacheValue.toString() : "");
        vo.setRemark(CACHE_NAMES.getOrDefault(cacheName, ""));

        return Result.success(vo);
    }

    /**
     * 清除指定缓存键
     *
     * @param cacheKey 缓存键名
     * @return 操作结果
     */
    @Operation(summary = "清除指定缓存", description = "根据键名删除指定的缓存")
    @DeleteMapping("/key/{cacheKey}")
    @PreAuthorize("@ss.hasPermi('monitor:cache:list')")
    public Result<Void> clearCacheKey(
            @Parameter(description = "缓存键名") @PathVariable String cacheKey) {
        redisCache.deleteObject(cacheKey);
        return Result.success();
    }

    /**
     * 清除指定名称下的所有缓存
     *
     * @param cacheName 缓存名称
     * @return 操作结果
     */
    @Operation(summary = "清除指定名称缓存", description = "清除指定缓存名称下的所有缓存")
    @DeleteMapping("/name/{cacheName}")
    @PreAuthorize("@ss.hasPermi('monitor:cache:list')")
    public Result<Void> clearCacheName(
            @Parameter(description = "缓存名称") @PathVariable String cacheName) {
        Collection<String> keys = redisCache.keys(cacheName + ":*");
        if (keys != null && !keys.isEmpty()) {
            redisCache.deleteObject(keys);
        }
        return Result.success();
    }

    /**
     * 清除所有缓存
     *
     * @return 操作结果
     */
    @Operation(summary = "清除所有缓存", description = "清除系统中的所有缓存（谨慎操作）")
    @DeleteMapping("/all")
    @PreAuthorize("@ss.hasPermi('monitor:cache:list')")
    public Result<Void> clearAllCache() {
        Collection<String> keys = redisCache.keys("*");
        if (keys != null && !keys.isEmpty()) {
            redisCache.deleteObject(keys);
        }
        return Result.success();
    }

    // ==================== VO 内部类 ====================

    /**
     * 缓存名称VO
     */
    public static class CacheNameVO {
        private String cacheName;
        private String remark;

        public String getCacheName() {
            return cacheName;
        }

        public void setCacheName(String cacheName) {
            this.cacheName = cacheName;
        }

        public String getRemark() {
            return remark;
        }

        public void setRemark(String remark) {
            this.remark = remark;
        }
    }

    /**
     * 缓存键名VO
     */
    public static class CacheKeyVO {
        private String cacheKey;
        private String remark;

        public String getCacheKey() {
            return cacheKey;
        }

        public void setCacheKey(String cacheKey) {
            this.cacheKey = cacheKey;
        }

        public String getRemark() {
            return remark;
        }

        public void setRemark(String remark) {
            this.remark = remark;
        }
    }

    /**
     * 缓存内容VO
     */
    public static class CacheContentVO {
        private String cacheName;
        private String cacheKey;
        private String cacheValue;
        private String remark;

        public String getCacheName() {
            return cacheName;
        }

        public void setCacheName(String cacheName) {
            this.cacheName = cacheName;
        }

        public String getCacheKey() {
            return cacheKey;
        }

        public void setCacheKey(String cacheKey) {
            this.cacheKey = cacheKey;
        }

        public String getCacheValue() {
            return cacheValue;
        }

        public void setCacheValue(String cacheValue) {
            this.cacheValue = cacheValue;
        }

        public String getRemark() {
            return remark;
        }

        public void setRemark(String remark) {
            this.remark = remark;
        }
    }
}
