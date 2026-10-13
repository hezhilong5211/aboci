package com.aurora.ai.agent.service;

import com.aurora.ai.agent.billing.BillingCalculator;
import com.aurora.ai.domain.SysAiModel;
import com.aurora.ai.mapper.SysAiModelMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MobileModelCatalogServiceTest {
    @Test
    void excludesUnconfiguredModelsAndExposesOnlyPublicPricesAndCapabilities() throws Exception {
        SysAiModelMapper mapper = mock(SysAiModelMapper.class);
        SysAiModel image = base("image", "luxai", 7L);
        image.setBeanPerImage1k(8L);
        image.setBeanPerImage2k(15L);
        image.setSupportedResolutions("1K,2K");
        image.setSupportedAspectRatios("1:1,16:9");
        image.setDefaultResolution("1K");
        image.setIsDefault(1);
        SysAiModel chat = base("chat", "luxai", 8L);
        chat.setBeanPerMtokenInput(100L);
        chat.setBeanPerMtokenOutput(300L);
        SysAiModel missingKey = base("chat", "luxai", 9L);
        missingKey.setApiKey(null);
        SysAiModel missingPrice = base("image", "luxai", 10L);
        missingPrice.setSupportedResolutions("1K");
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(image, chat, missingKey, missingPrice));

        var result = new MobileModelCatalogService(mapper, new BillingCalculator()).catalog();
        assertThat(result.get("chatModels")).hasSize(1);
        assertThat(result.get("imageModels")).hasSize(1);
        assertThat(result.get("imageModels").getFirst().getBeanCosts()).containsEntry("1K", 8L).containsEntry("2K", 15L);
        assertThat(result.get("imageModels").getFirst().isSupportsReferenceImages()).isTrue();
        assertThat(result.get("imageModels").getFirst().getMaxReferenceImages()).isEqualTo(9);
        assertThat(result.get("chatModels").getFirst().getBeanPerMtokenInputCached()).isEqualTo(100L);
        String json = new ObjectMapper().writeValueAsString(result);
        assertThat(json).contains("\"isDefault\":true").doesNotContain("apiKey", "secretKey", "baseUrl", "encrypted-test-key", "provider.invalid");
    }

    private SysAiModel base(String type, String provider, long id) {
        SysAiModel model = new SysAiModel();
        model.setId(id);
        model.setModelType(type);
        model.setProvider(provider);
        model.setIsActive(1);
        model.setModelName("public model");
        model.setModelDeployment("deployment");
        model.setApiKey("encrypted-test-key");
        model.setSecretKey("encrypted-other-test-key");
        model.setBaseUrl("https://provider.invalid/v1");
        return model;
    }
}
