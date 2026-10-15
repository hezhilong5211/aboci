package com.aurora.ai.agent.service;

import com.aurora.ai.agent.billing.BillingCalculator;
import com.aurora.ai.domain.SysAiModel;
import com.aurora.ai.mapper.SysAiModelMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

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
        assertThat(first).extracting(model -> model.getDisplayTier()).containsExactly("轻", "中", "高");
        assertThat(first).extracting(model -> model.getModelId()).containsExactly(1L, 3L, 2L);
        light.setIsDefault(1);
        light.setModelName("renamed private model");
        unknown.setDisplayTier(null);
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(light, high, unknown));
        var second = service.catalog().get("chatModels");
        assertThat(second).extracting(model -> model.getModelName()).containsExactly("轻", "中", "高");
        assertThat(second).extracting(model -> model.getModelId()).containsExactly(1L, 3L, 2L);
    }

    @Test
    void sortsImageTiersAscendingWithoutChangingDefaultsModelIdsOrPrices() {
        SysAiModelMapper mapper = mock(SysAiModelMapper.class);
        SysAiModel light = image(11L, "轻", 6L, 8L, 10L);
        SysAiModel middle = image(12L, "中", 10L, 15L, 20L);
        SysAiModel high = image(13L, "高", 15L, 20L, 30L);
        middle.setIsDefault(1);
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(middle, high, light));

        var result = new MobileModelCatalogService(mapper, new BillingCalculator()).catalog().get("imageModels");

        assertThat(result).extracting(model -> model.getDisplayTier()).containsExactly("轻", "中", "高");
        assertThat(result).extracting(model -> model.getModelId()).containsExactly(11L, 12L, 13L);
        assertThat(result).extracting(model -> model.getIsDefault()).containsExactly(false, true, false);
        assertThat(result).extracting(model -> model.getBeanCosts()).containsExactly(
                Map.of("1K", 6L, "2K", 8L, "4K", 10L),
                Map.of("1K", 10L, "2K", 15L, "4K", 20L),
                Map.of("1K", 15L, "2K", 20L, "4K", 30L));
    }

    @Test
    void preservesExistingOrderWithinTheSameTier() {
        SysAiModelMapper mapper = mock(SysAiModelMapper.class);
        SysAiModel defaultMiddle = image(23L, "中", 10L, 15L, 20L);
        defaultMiddle.setIsDefault(1);
        SysAiModel unknownTier = image(22L, "private-tier", 11L, 16L, 21L);
        SysAiModel otherMiddle = image(21L, "中", 12L, 17L, 22L);
        SysAiModel light = image(24L, "轻", 6L, 8L, 10L);
        when(mapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(defaultMiddle, unknownTier, light, otherMiddle));

        var result = new MobileModelCatalogService(mapper, new BillingCalculator()).catalog().get("imageModels");

        assertThat(result).extracting(model -> model.getModelName()).containsExactly("轻", "中", "中", "中");
        assertThat(result).extracting(model -> model.getModelId()).containsExactly(24L, 23L, 22L, 21L);
    }

    private SysAiModel image(long id, String tier, long price1k, long price2k, long price4k) {
        SysAiModel model = base("image", "luxai", id);
        model.setDisplayTier(tier);
        model.setSupportedResolutions("1K,2K,4K");
        model.setBeanPerImage1k(price1k);
        model.setBeanPerImage2k(price2k);
        model.setBeanPerImage4k(price4k);
        return model;
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
