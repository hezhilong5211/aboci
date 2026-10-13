package com.aurora.admin.mobile.service.impl;

import com.aurora.admin.mobile.dto.GenerationConfigDTO;
import com.aurora.admin.mobile.service.IGenerationConfigService;
import com.aurora.common.core.constant.MobileGenerationConfigKeys;
import com.aurora.common.core.domain.entity.SysConfig;
import com.aurora.common.core.exception.ServiceException;
import com.aurora.common.core.service.ISysConfigService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 小程序 AI 生成配置实现，服务端固定键名，禁止通过接口读写其他系统参数。
 */
@Service
@RequiredArgsConstructor
public class GenerationConfigServiceImpl implements IGenerationConfigService {

    private final ISysConfigService sysConfigService;

    @Override
    public GenerationConfigDTO getConfig() {
        GenerationConfigDTO result = new GenerationConfigDTO();
        result.setGenerationEnabled(readEnabled(MobileGenerationConfigKeys.GENERATION_ENABLED));
        result.setDownloadWatermarkEnabled(
                readEnabled(MobileGenerationConfigKeys.DOWNLOAD_WATERMARK_ENABLED));
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Caching(evict = {
            @CacheEvict(value = "config", key = "'mobile.generation.enabled'"),
            @CacheEvict(value = "config", key = "'mobile.generation.download_watermark.enabled'")
    })
    public void updateConfig(GenerationConfigDTO config) {
        updateExisting(MobileGenerationConfigKeys.GENERATION_ENABLED,
                Boolean.TRUE.equals(config.getGenerationEnabled()));
        updateExisting(MobileGenerationConfigKeys.DOWNLOAD_WATERMARK_ENABLED,
                Boolean.TRUE.equals(config.getDownloadWatermarkEnabled()));
    }

    private boolean readEnabled(String key) {
        return MobileGenerationConfigKeys.enabledOrDefault(sysConfigService.selectConfigByKey(key));
    }

    private void updateExisting(String key, boolean enabled) {
        SysConfig existing = sysConfigService.getOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, key)
                .orderByAsc(SysConfig::getConfigId)
                .last("LIMIT 1"), false);
        if (existing == null) {
            throw new ServiceException("AI 生成配置项不存在，请检查数据库迁移：" + key);
        }

        SysConfig update = new SysConfig();
        update.setConfigId(existing.getConfigId());
        update.setConfigValue(Boolean.toString(enabled));
        if (!sysConfigService.updateById(update)) {
            throw new ServiceException("AI 生成配置保存失败：" + key);
        }
    }
}
