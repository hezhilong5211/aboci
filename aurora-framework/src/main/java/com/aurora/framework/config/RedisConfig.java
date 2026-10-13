package com.aurora.framework.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis 缓存配置
 * 
 * 缓存区域说明：
 * - dict: 字典数据缓存，过期时间 24 小时
 * - config: 系统配置缓存，过期时间 24 小时
 * - user: 用户信息缓存，过期时间 30 分钟
 * - aiModel: AI 模型配置缓存，过期时间 1 小时
 * - menu: 菜单数据缓存，过期时间 24 小时
 * - default: 默认缓存，过期时间 1 小时
 * 
 * @author aurora
 */
@Configuration
@EnableCaching
public class RedisConfig {

    /**
     * 缓存区域名称常量
     */
    public static final String CACHE_DICT = "dict";
    public static final String CACHE_CONFIG = "config";
    public static final String CACHE_USER = "user";
    public static final String CACHE_AI_MODEL = "aiModel";
    public static final String CACHE_MENU = "menu";
    public static final String CACHE_DEFAULT = "default";

    /**
     * 创建用于缓存的 ObjectMapper
     */
    private ObjectMapper createCacheObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        // 安全加固:用白名单多态校验器替代“放行一切”的 LaissezFaire,
        // 仅允许反序列化本框架(com.aurora.*)与 JDK 基础包的类型,阻断借第三方 gadget 类的
        // 多态反序列化攻击(RCE 链);保留 NON_FINAL + @class 以兼容既有缓存数据。
        BasicPolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.aurora.")
                .allowIfSubType("java.util.")
                .allowIfSubType("java.time.")
                .allowIfSubType("java.lang.")
                .build();
        mapper.activateDefaultTyping(
                ptv,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        return mapper;
    }

    /**
     * 缓存管理器配置
     * 支持多个缓存区域，每个区域可以有不同的过期时间
     */
    @Bean
    @Primary
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(
                createCacheObjectMapper());

        // 默认缓存配置
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1))
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer))
                .disableCachingNullValues(); // 不缓存 null 值

        // 各缓存区域配置
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // 字典缓存：24 小时
        cacheConfigurations.put(CACHE_DICT, defaultConfig.entryTtl(Duration.ofHours(24)));

        // 系统配置缓存：24 小时
        cacheConfigurations.put(CACHE_CONFIG, defaultConfig.entryTtl(Duration.ofHours(24)));

        // 用户信息缓存：30 分钟（用户信息变化较频繁）
        cacheConfigurations.put(CACHE_USER, defaultConfig.entryTtl(Duration.ofMinutes(30)));

        // AI 模型配置缓存：1 小时
        cacheConfigurations.put(CACHE_AI_MODEL, defaultConfig.entryTtl(Duration.ofHours(1)));

        // 菜单缓存：24 小时
        cacheConfigurations.put(CACHE_MENU, defaultConfig.entryTtl(Duration.ofHours(24)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware() // 事务感知，确保缓存操作与数据库事务一致
                .build();
    }

    /**
     * RedisTemplate 配置
     * 用于直接操作 Redis
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(
                createCacheObjectMapper());

        // Key 使用 String 序列化
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(jsonSerializer);

        // Hash Key 和 Value 序列化
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }
}
