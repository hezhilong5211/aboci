package com.aurora.admin.mobile.service;

import com.aurora.admin.mobile.dto.ShareConfigDTO;
import com.aurora.admin.mobile.service.impl.ShareConfigServiceImpl;
import com.aurora.common.core.domain.entity.SysConfig;
import com.aurora.common.core.service.ISysConfigService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShareConfigServiceImplTest {

    @Mock
    private ISysConfigService sysConfigService;

    private ShareConfigServiceImpl shareConfigService;

    @BeforeEach
    void setUp() {
        shareConfigService = new ShareConfigServiceImpl(sysConfigService);
    }

    @Test
    void getShareConfigReadsOnlyFixedShareKeys() {
        when(sysConfigService.selectConfigByKey("mobile.share.title")).thenReturn("Aboci 分享");
        when(sysConfigService.selectConfigByKey("mobile.share.image")).thenReturn("/profile/share.png");

        ShareConfigDTO result = shareConfigService.getShareConfig();

        assertThat(result.getTitle()).isEqualTo("Aboci 分享");
        assertThat(result.getImage()).isEqualTo("/profile/share.png");
        verify(sysConfigService).selectConfigByKey("mobile.share.title");
        verify(sysConfigService).selectConfigByKey("mobile.share.image");
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateShareConfigUpdatesOnlyExistingShareRows() {
        SysConfig title = config(11L, "mobile.share.title");
        SysConfig image = config(12L, "mobile.share.image");
        when(sysConfigService.getOne(any(LambdaQueryWrapper.class), eq(false)))
                .thenReturn(title, image);
        when(sysConfigService.updateById(any(SysConfig.class))).thenReturn(true);

        ShareConfigDTO input = new ShareConfigDTO();
        input.setTitle("新的分享标题");
        input.setImage("/profile/new-share.png");
        shareConfigService.updateShareConfig(input);

        ArgumentCaptor<SysConfig> updates = ArgumentCaptor.forClass(SysConfig.class);
        verify(sysConfigService, org.mockito.Mockito.times(2)).updateById(updates.capture());
        List<SysConfig> values = updates.getAllValues();
        assertThat(values).extracting(SysConfig::getConfigId).containsExactly(11L, 12L);
        assertThat(values).extracting(SysConfig::getConfigValue)
                .containsExactly("新的分享标题", "/profile/new-share.png");
        assertThat(values).allMatch(item -> item.getConfigKey() == null);
        verify(sysConfigService, never()).save(any(SysConfig.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateShareConfigFailsIfRequiredMigrationRowsAreMissing() {
        when(sysConfigService.getOne(any(LambdaQueryWrapper.class), eq(false)))
                .thenReturn((SysConfig) null);

        ShareConfigDTO input = new ShareConfigDTO();
        assertThatThrownBy(() -> shareConfigService.updateShareConfig(input))
                .hasMessageContaining("mobile.share.title");

        verify(sysConfigService, never()).save(any(SysConfig.class));
        verify(sysConfigService, never()).updateById(any(SysConfig.class));
    }

    private SysConfig config(Long id, String key) {
        SysConfig config = new SysConfig();
        config.setConfigId(id);
        config.setConfigKey(key);
        return config;
    }
}
