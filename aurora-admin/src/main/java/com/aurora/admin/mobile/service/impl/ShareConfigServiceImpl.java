package com.aurora.admin.mobile.service.impl;

import com.aurora.admin.mobile.dto.ShareConfigDTO;
import com.aurora.admin.mobile.service.IShareConfigService;
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
 * 小程序分享配置服务实现。
 *
 * 服务端固定配置键，调用方不能借此读取或修改其他 sys_config 数据。
 */
@Service
@RequiredArgsConstructor
public class ShareConfigServiceImpl implements IShareConfigService {

    static final String TITLE_KEY = "mobile.share.title";
    static final String IMAGE_KEY = "mobile.share.image";

    private final ISysConfigService sysConfigService;

    @Override
    public ShareConfigDTO getShareConfig() {
        ShareConfigDTO result = new ShareConfigDTO();
        result.setTitle(valueOrEmpty(sysConfigService.selectConfigByKey(TITLE_KEY)));
        result.setImage(valueOrEmpty(sysConfigService.selectConfigByKey(IMAGE_KEY)));
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Caching(evict = {
            @CacheEvict(value = "config", key = "'mobile.share.title'"),
            @CacheEvict(value = "config", key = "'mobile.share.image'")
    })
    public void updateShareConfig(ShareConfigDTO config) {
        updateExisting(TITLE_KEY, valueOrEmpty(config.getTitle()));
        updateExisting(IMAGE_KEY, valueOrEmpty(config.getImage()));
    }

    private void updateExisting(String key, String value) {
        SysConfig existing = sysConfigService.getOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, key)
                .orderByAsc(SysConfig::getConfigId)
                .last("LIMIT 1"), false);

        if (existing == null) {
            throw new ServiceException("分享配置项不存在，请检查数据库迁移：" + key);
        }

        SysConfig update = new SysConfig();
        update.setConfigId(existing.getConfigId());
        update.setConfigValue(value);
        if (!sysConfigService.updateById(update)) {
            throw new ServiceException("分享配置保存失败：" + key);
        }
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
