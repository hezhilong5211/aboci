package com.aurora.common.core.service.impl;

import com.aurora.common.core.domain.entity.SysConfig;
import com.aurora.common.core.mapper.SysConfigMapper;
import com.aurora.common.core.service.ISysConfigService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * 参数配置业务层处理
 * 使用 Redis 缓存提升查询性能
 */
@Service
public class SysConfigServiceImpl extends ServiceImpl<SysConfigMapper, SysConfig> implements ISysConfigService {

    /**
     * 根据配置键查询配置值
     * 缓存 24 小时
     */
    @Override
    @Cacheable(value = "config", key = "#configKey", unless = "#result == null")
    public String selectConfigByKey(String configKey) {
        // throwEx=false:configKey 在脏数据/并发下可能命中多行,避免 getOne 抛 TooManyResults 使配置读取链路崩
        SysConfig config = getOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, configKey)
                .orderByAsc(SysConfig::getConfigId)
                .last("LIMIT 1"), false);
        return config != null ? config.getConfigValue() : null;
    }

    /**
     * 清除指定配置的缓存
     */
    @CacheEvict(value = "config", key = "#configKey")
    public void clearConfigCache(String configKey) {
        // 仅清除缓存
    }

    /**
     * 清除所有配置缓存
     */
    @CacheEvict(value = "config", allEntries = true)
    public void clearAllConfigCache() {
        // 清除所有配置缓存
    }
}
