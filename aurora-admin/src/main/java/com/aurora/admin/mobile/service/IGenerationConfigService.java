package com.aurora.admin.mobile.service;

import com.aurora.admin.mobile.dto.GenerationConfigDTO;

/**
 * 小程序 AI 生成固定配置服务。
 */
public interface IGenerationConfigService {

    GenerationConfigDTO getConfig();

    void updateConfig(GenerationConfigDTO config);
}
