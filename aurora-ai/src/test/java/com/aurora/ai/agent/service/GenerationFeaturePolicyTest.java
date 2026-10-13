package com.aurora.ai.agent.service;

import com.aurora.common.core.constant.MobileGenerationConfigKeys;
import com.aurora.common.core.exception.ServiceException;
import com.aurora.common.core.service.ISysConfigService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GenerationFeaturePolicyTest {

    private final ISysConfigService sysConfigService = mock(ISysConfigService.class);
    private final GenerationFeaturePolicy policy = new GenerationFeaturePolicy(sysConfigService);

    @Test
    void 独立生成功能关闭时拒绝提交并返回固定提示() {
        when(sysConfigService.selectConfigByKey(MobileGenerationConfigKeys.GENERATION_ENABLED))
                .thenReturn("false");

        assertThatThrownBy(policy::requireStandaloneGenerationEnabled)
                .isInstanceOf(ServiceException.class)
                .hasMessage("当前功能正在开发中");
    }

    @Test
    void 独立生成配置缺失时保持兼容开启() {
        when(sysConfigService.selectConfigByKey(MobileGenerationConfigKeys.GENERATION_ENABLED))
                .thenReturn(null);

        assertThatCode(policy::requireStandaloneGenerationEnabled).doesNotThrowAnyException();
    }

    @Test
    void 下载水印读取独立配置() {
        when(sysConfigService.selectConfigByKey(
                MobileGenerationConfigKeys.DOWNLOAD_WATERMARK_ENABLED)).thenReturn("0");

        assertThat(policy.isDownloadWatermarkEnabled()).isFalse();
    }
}
