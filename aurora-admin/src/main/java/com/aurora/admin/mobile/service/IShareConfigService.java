package com.aurora.admin.mobile.service;

import com.aurora.admin.mobile.dto.ShareConfigDTO;

/**
 * 小程序分享配置服务。
 */
public interface IShareConfigService {

    /**
     * 查询分享配置。
     */
    ShareConfigDTO getShareConfig();

    /**
     * 更新分享配置。
     */
    void updateShareConfig(ShareConfigDTO config);
}
