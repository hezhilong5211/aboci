package com.aurora.admin.mobile.service;

import com.aurora.admin.mobile.dto.GenerationConfigDTO;
import com.aurora.admin.mobile.service.impl.GenerationConfigServiceImpl;
import com.aurora.common.core.domain.entity.SysConfig;
import com.aurora.common.core.service.ISysConfigService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenerationConfigServiceImplTest {

    @Mock
    private ISysConfigService sysConfigService;

    @Test
    void 缺失或脏配置默认保持开启() {
        when(sysConfigService.selectConfigByKey("mobile.generation.enabled")).thenReturn(null);
        when(sysConfigService.selectConfigByKey("mobile.generation.download_watermark.enabled"))
                .thenReturn("unexpected");

        GenerationConfigDTO result = new GenerationConfigServiceImpl(sysConfigService).getConfig();

        assertThat(result.getGenerationEnabled()).isTrue();
        assertThat(result.getDownloadWatermarkEnabled()).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void 更新只写两个固定配置键对应行() {
        SysConfig generation = config(31L);
        SysConfig watermark = config(32L);
        when(sysConfigService.getOne(any(LambdaQueryWrapper.class), eq(false)))
                .thenReturn(generation, watermark);
        when(sysConfigService.updateById(any(SysConfig.class))).thenReturn(true);
        GenerationConfigDTO input = new GenerationConfigDTO();
        input.setGenerationEnabled(false);
        input.setDownloadWatermarkEnabled(true);

        new GenerationConfigServiceImpl(sysConfigService).updateConfig(input);

        ArgumentCaptor<SysConfig> captor = ArgumentCaptor.forClass(SysConfig.class);
        verify(sysConfigService, times(2)).updateById(captor.capture());
        List<SysConfig> updates = captor.getAllValues();
        assertThat(updates).extracting(SysConfig::getConfigId).containsExactly(31L, 32L);
        assertThat(updates).extracting(SysConfig::getConfigValue).containsExactly("false", "true");
        assertThat(updates).allMatch(item -> item.getConfigKey() == null);
    }

    @Test
    @SuppressWarnings("unchecked")
    void 迁移配置缺失时拒绝静默新增() {
        when(sysConfigService.getOne(any(LambdaQueryWrapper.class), eq(false))).thenReturn(null);
        GenerationConfigDTO input = new GenerationConfigDTO();
        input.setGenerationEnabled(true);
        input.setDownloadWatermarkEnabled(true);

        assertThatThrownBy(() -> new GenerationConfigServiceImpl(sysConfigService).updateConfig(input))
                .hasMessageContaining("mobile.generation.enabled");
    }

    private SysConfig config(Long id) {
        SysConfig config = new SysConfig();
        config.setConfigId(id);
        return config;
    }
}
