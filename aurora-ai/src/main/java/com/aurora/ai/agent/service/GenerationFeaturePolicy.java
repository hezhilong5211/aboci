package com.aurora.ai.agent.service;

import com.aurora.common.core.constant.MobileGenerationConfigKeys;
import com.aurora.common.core.exception.ServiceException;
import com.aurora.common.core.service.ISysConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 独立生成页面与生成图片下载的运行时开关策略。
 */
@Service
@RequiredArgsConstructor
public class GenerationFeaturePolicy {

    private final ISysConfigService sysConfigService;

    public boolean isGenerationEnabled() {
        return read(MobileGenerationConfigKeys.GENERATION_ENABLED);
    }

    public boolean isDownloadWatermarkEnabled() {
        return read(MobileGenerationConfigKeys.DOWNLOAD_WATERMARK_ENABLED);
    }

    public void requireStandaloneGenerationEnabled() {
        if (!isGenerationEnabled()) {
            throw new ServiceException("当前功能正在开发中");
        }
    }

    private boolean read(String key) {
        return MobileGenerationConfigKeys.enabledOrDefault(sysConfigService.selectConfigByKey(key));
    }
}
