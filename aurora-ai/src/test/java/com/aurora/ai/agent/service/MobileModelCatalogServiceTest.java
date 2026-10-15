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
        image.setModelName("GPT Image 2.5 Sunburst (Luxai)");
        image.setModelDeployment("gpt-image-2.5-sunburst");
        image.setDisplayTier("高");
        image.setBeanPerImage1k(8L);
        image.setBeanPerImage2k(15L);
        image.setSupportedResolutions("1K,2K");
        image.setSupportedAspectRatios("1:1,16:9");
        image.setDefaultResolution("1K");
        image.setIsDefault(1);
        SysAiModel chat = base("chat", "luxai", 8L);
        chat.setModelName("GPT-6.1 Sol (Luxai)");
        chat.setModelDeployment("gpt-6.1-sol");
        chat.setDisplayTier("高");
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
        assertThat(result.get("imageModels").getFirst().getModelName()).isEqualTo("高");
        assertThat(result.get("imageModels").getFirst().getModelId()).isEqualTo(7L);
        assertThat(result.get("chatModels").getFirst().getModelName()).isEqualTo("高");
        assertThat(result.get("imageModels").getFirst().getBeanCosts()).containsEntry("1K", 8L).containsEntry("2K", 15L);
        assertThat(result.get("imageModels").getFirst().isSupportsReferenceImages()).isTrue();
        assertThat(result.get("imageModels").getFirst().getMaxReferenceImages()).isEqualTo(9);
        assertThat(result.get("chatModels").getFirst().getBeanPerMtokenInputCached()).isEqualTo(100L);
        String json = new ObjectMapper().writeValueAsString(result);
        assertThat(json).contains("\"isDefault\":true")
                .doesNotContain("apiKey", "secretKey", "baseUrl", "encrypted-test-key", "provider",
                        "modelDeployment", "GPT", "gpt-", "Sunburst", "luxai");
        assertThat(image.getModelName()).isEqualTo("GPT Image 2.5 Sunburst (Luxai)");
        assertThat(chat.getModelDeployment()).isEqualTo("gpt-6.1-sol");
    }

    @Test
    void publicTiersStayBoundToModelWhenDefaultsOrderAndNamesChange() {
        SysAiModelMapper mapper = mock(SysAiModelMapper.class);
        SysAiModel light = base("chat", "luxai", 1L);
        SysAiModel high = base("chat", "luxai", 2L);
        SysAiModel unknown = base("chat", "luxai", 3L);
        for (SysAiModel model : List.of(light, high, unknown)) {
            model.setBeanPerMtokenInput(25L);
            model.setBeanPerMtokenOutput(25L);
        }
        light.setDisplayTier("轻");
        high.setDisplayTier("高");
        unknown.setDisplayTier("internal model name");
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(high, unknown, light));
        var service = new MobileModelCatalogService(mapper, new BillingCalculator());
        var first = service.catalog().get("chatModels");
        assertThat(first).extracting(model -> model.getDisplayTier()).containsExactly("高", "中", "轻");
        light.setIsDefault(1);
        light.setModelName("renamed private model");
        unknown.setDisplayTier(null);
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(light, high, unknown));
        var second = service.catalog().get("chatModels");
        assertThat(second).extracting(model -> model.getModelName()).containsExactly("轻", "高", "中");
        assertThat(second).extracting(model -> model.getModelId()).containsExactly(1L, 2L, 3L);
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
