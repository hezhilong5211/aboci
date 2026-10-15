package com.aurora.ai.controller;

import com.aurora.ai.agent.mapper.GenerationTaskMapper;
import com.aurora.ai.domain.SysAiModel;
import com.aurora.ai.mapper.SysAiModelMapper;
import com.aurora.ai.provider.GeminiVisionClient;
import com.aurora.ai.provider.LuxaiImageClient;
import com.aurora.ai.provider.ResponsesApiClient;
import com.aurora.ai.service.AiFactory;
import com.aurora.framework.utils.EncryptUtils;
import com.aurora.common.core.annotation.Log;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SysAiModelConfigurationTest {
    private final SysAiModelMapper models = mock(SysAiModelMapper.class);
    private final EncryptUtils encryption = mock(EncryptUtils.class);
    private final GenerationTaskMapper tasks = mock(GenerationTaskMapper.class);
    private final SysAiModelController controller = new SysAiModelController(models, mock(AiFactory.class), encryption,
            mock(GeminiVisionClient.class), mock(ResponsesApiClient.class), mock(LuxaiImageClient.class), tasks);

    @BeforeEach
    void metadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SysAiModel.class);
    }

    @Test
    void encryptsNewLuxaiKeyAndRejectsUnpricedActivation() {
        SysAiModel model = imageModel();
        model.setApiKey("private-local-test-key");
        when(encryption.encrypt("private-local-test-key")).thenReturn("ENC2:encrypted-local-test-key");
        assertThat(controller.add(model).getCode()).isEqualTo(200);
        assertThat(model.getApiKey()).isEqualTo("ENC2:encrypted-local-test-key");
        verify(models).insert(model);
        clearInvocations(models);
        model.setBeanPerImage1k(null);
        assertThat(controller.add(model).getMsg()).contains("每张消耗豆子");
        verify(models, never()).insert(any(SysAiModel.class));
    }

    @Test
    void maskedEditPreservesCredentialAndPartialUpdateValidatesStoredFields() {
        SysAiModel stored = imageModel();
        stored.setId(14L);
        stored.setApiKey("ENC2:encrypted-local-test-key");
        when(models.selectById(14L)).thenReturn(stored);
        SysAiModel update = new SysAiModel();
        update.setId(14L);
        update.setApiKey("pri****key");
        update.setImageQuality("high");
        assertThat(controller.edit(update).getCode()).isEqualTo(200);
        assertThat(update.getApiKey()).isNull();
        verify(models).updateById(update);
        verifyNoInteractions(encryption);
    }

    @Test
    void gpt61DefaultsToResponsesAndCannotUseChatCompletions() {
        SysAiModel model = new SysAiModel();
        model.setModelType("chat");
        model.setModelDeployment("gpt-6.1-sol");
        model.setProvider("luxai");
        model.setIsActive(0);
        assertThat(controller.add(model).getCode()).isEqualTo(200);
        assertThat(model.getApiProtocol()).isEqualTo("responses");
        model.setApiProtocol("chat_completions");
        assertThat(controller.add(model).getMsg()).contains("Responses");
    }

    @Test
    void publicTierDefaultsOnCreateRejectsPrivateNamesAndSurvivesPartialEdit() {
        SysAiModel model = new SysAiModel();
        model.setModelType("chat");
        model.setIsActive(0);
        assertThat(controller.add(model).getCode()).isEqualTo(200);
        assertThat(model.getDisplayTier()).isEqualTo("中");
        clearInvocations(models);
        for (String invalid : java.util.List.of("GPT-6.1 Sol", "", " 高 ")) {
            model.setDisplayTier(invalid);
            assertThat(controller.add(model).getMsg()).contains("小程序档位");
        }
        verify(models, never()).insert(any(SysAiModel.class));
        model.setId(14L);
        model.setDisplayTier("高");
        when(models.selectById(14L)).thenReturn(model);
        SysAiModel update = new SysAiModel();
        update.setId(14L);
        update.setModelName("internal rename");
        assertThat(controller.edit(update).getCode()).isEqualTo(200);
        assertThat(model.getDisplayTier()).isEqualTo("高");
        assertThat(update.getDisplayTier()).isNull();
        clearInvocations(models);
        update.setDisplayTier("internal private model");
        assertThat(controller.edit(update).getMsg()).contains("小程序档位");
        verify(models, never()).updateById(any(SysAiModel.class));
    }

    @Test
    void disabledOrUncredentialedModelCannotBecomeDefaultAndReferencedModelCannotBeDeleted() {
        SysAiModel model = imageModel();
        model.setId(14L);
        model.setIsActive(0);
        when(models.selectById(14L)).thenReturn(model);
        assertThat(controller.setDefault(14L).getMsg()).contains("启用并验证");
        model.setIsActive(1);
        assertThat(controller.setDefault(14L).getMsg()).contains("API Key");
        when(tasks.selectCount(any())).thenReturn(1L);
        assertThat(controller.remove(new Long[]{14L}).getMsg()).contains("请停用模型");
        verify(models, never()).deleteBatchIds(anyCollection());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void settingGpt61DefaultDisablesOnlyGpt56AndPreservesRollbackCredentialsAndPrices() {
        SysAiModel model = new SysAiModel();
        model.setId(20L);
        model.setModelType("chat");
        model.setModelDeployment("gpt-6.1-sol");
        model.setApiProtocol("responses");
        model.setBaseUrl("https://api.openlux.ai/v1");
        model.setApiKey("ENC2:encrypted-local-key");
        model.setIsActive(1);
        model.setBeanPerMtokenInput(25L);
        model.setBeanPerMtokenOutput(25L);
        when(models.selectById(20L)).thenReturn(model);
        assertThat(controller.setDefault(20L).getCode()).isEqualTo(200);
        ArgumentCaptor<LambdaUpdateWrapper> updates = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(models, times(3)).update(isNull(), updates.capture());
        LambdaUpdateWrapper old = updates.getAllValues().get(2);
        assertThat(old.getSqlSegment()).contains("model_type", "model_deployment");
        assertThat(old.getSqlSet()).contains("is_active", "is_default").doesNotContain("api_key", "bean_per");
        assertThat(old.getParamNameValuePairs().values()).contains("chat", "gpt-5.6-sol", 0);
    }

    @Test
    void auditKeepsRequestBodiesContainingCredentialsOutOfOperationLogs() throws Exception {
        for (String operation : java.util.List.of("add", "edit")) {
            Log annotation = SysAiModelController.class.getMethod(operation, SysAiModel.class).getAnnotation(Log.class);
            assertThat(annotation.isSaveRequestData()).isFalse();
        }
    }

    private SysAiModel imageModel() {
        SysAiModel model = new SysAiModel();
        model.setModelType("image");
        model.setProvider("luxai");
        model.setModelDeployment("gpt-image-2.5-flare");
        model.setBaseUrl("https://api.openlux.ai/v1");
        model.setIsActive(1);
        model.setSupportedResolutions("1K");
        model.setSupportedAspectRatios("1:1,16:9");
        model.setDefaultResolution("1K");
        model.setBeanPerImage1k(6L);
        return model;
    }
}
