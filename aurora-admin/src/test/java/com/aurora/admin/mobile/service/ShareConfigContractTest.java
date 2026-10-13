package com.aurora.admin.mobile.service;

import com.aurora.admin.controller.mobile.ShareConfigController;
import com.aurora.admin.mobile.dto.ShareConfigDTO;
import com.aurora.admin.mobile.service.impl.ShareConfigServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ShareConfigContractTest {

    @Test
    void controllerUsesDedicatedReadAndEditPermissions() throws Exception {
        Method read = ShareConfigController.class.getMethod("getShareConfig");
        Method edit = ShareConfigController.class.getMethod("updateShareConfig", ShareConfigDTO.class);

        assertThat(read.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("@ss.hasAnyPermi('system:shareconfig:list', 'system:shareconfig:edit')");
        assertThat(edit.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("@ss.hasPermi('system:shareconfig:edit')");
    }

    @Test
    void requestContractCannotCarryArbitrarySystemConfigIdentifiers() {
        assertThat(Arrays.stream(ShareConfigDTO.class.getDeclaredFields()).map(Field::getName))
                .contains("title", "image")
                .doesNotContain("configId", "configKey", "configType", "configValue");
    }

    @Test
    void updateIsTransactionalAndEvictsBothPublicShareCacheEntries() throws Exception {
        Method update = ShareConfigServiceImpl.class.getMethod("updateShareConfig", ShareConfigDTO.class);

        assertThat(update.getAnnotation(Transactional.class)).isNotNull();
        Caching caching = update.getAnnotation(Caching.class);
        assertThat(caching).isNotNull();
        assertThat(Arrays.stream(caching.evict()).map(CacheEvict::key))
                .containsExactlyInAnyOrder("'mobile.share.title'", "'mobile.share.image'");
    }
}
