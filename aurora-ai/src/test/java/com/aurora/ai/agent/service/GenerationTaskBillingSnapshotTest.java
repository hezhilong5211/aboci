package com.aurora.ai.agent.service;

import com.aurora.ai.agent.billing.BillingCalculator;
import com.aurora.ai.agent.billing.IBeanDeductPort;
import com.aurora.ai.agent.domain.GenerationTask;
import com.aurora.ai.agent.dto.GenerationTaskVO;
import com.aurora.ai.agent.dto.GenerateImageItem;
import com.aurora.ai.agent.mapper.GenerationTaskMapper;
import com.aurora.ai.agent.service.impl.GenerationTaskServiceImpl;
import com.aurora.ai.domain.SysAiModel;
import com.aurora.ai.mapper.SysAiModelMapper;
import com.aurora.common.core.exception.ServiceException;
import com.aurora.framework.mq.TaskProducer;
import com.aurora.framework.storage.ProfileStorage;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GenerationTaskBillingSnapshotTest {
    private GenerationTaskServiceImpl service;
    private final GenerationTaskMapper taskMapper = mock(GenerationTaskMapper.class);
    private final SysAiModelMapper models = mock(SysAiModelMapper.class);
    private final IBeanDeductPort beans = mock(IBeanDeductPort.class);
    private final TaskProducer queue = mock(TaskProducer.class);
    private SysAiModel model;
    private final ProfileStorage storage = mock(ProfileStorage.class);

    @BeforeEach
    void setUp() throws Exception {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), GenerationTask.class);
        service = spy(new GenerationTaskServiceImpl(new ObjectMapper(), queue, models, new BillingCalculator(), storage));
        ReflectionTestUtils.setField(service, "baseMapper", taskMapper);
        ReflectionTestUtils.setField(service, "beanDeductPort", beans);
        when(storage.size(anyString())).thenReturn(1024L);
        model = new SysAiModel();
        model.setId(14L);
        model.setModelType("image");
        model.setProvider("luxai");
        model.setIsActive(1);
        model.setApiKey("encrypted");
        model.setModelName("GPT Image 2.5 Flare");
        model.setDisplayTier("中");
        model.setModelDeployment("gpt-image-2.5-flare");
        model.setBaseUrl("https://api.openlux.ai/v1");
        model.setSupportedResolutions("1K,2K");
        model.setSupportedAspectRatios("1:1,16:9");
        model.setDefaultResolution("1K");
        model.setBeanPerImage1k(8L);
        model.setBeanPerImage2k(15L);
        model.setImageQuality("high");
        when(models.selectById(14L)).thenReturn(model);
        when(beans.getBalance(7L)).thenReturn(1000L);
        when(taskMapper.insert(any(GenerationTask.class))).thenAnswer(invocation -> {
            GenerationTask task = invocation.getArgument(0);
            task.setId(101L);
            return 1;
        });
        when(taskMapper.updateById(any(GenerationTask.class))).thenReturn(1);
        doReturn(null).when(service).getOne(any(LambdaQueryWrapper.class));
        doNothing().when(service).markStatus(anyString(), anyString(), any(), any(), any());
    }

    @Test
    void publicOptionsAndTaskRecoveryHideModelAndSupplierButKeepPricesAndSelection() throws Exception {
        var options = service.getGenerationOptions(14L);
        ObjectMapper json = new ObjectMapper();
        assertThat(options.getModelName()).isEqualTo("中");
        assertThat(options.getModelId()).isEqualTo(14L);
        assertThat(options.getBeanCosts()).containsEntry("1K", 8L).containsEntry("2K", 15L);
        assertThat(json.writeValueAsString(options))
                .doesNotContain("GPT", "gpt-", "Flare", "provider", "luxai", "modelDeployment");

        service.submitImageGeneration(7L, null, "draw", 2, "2K", "16:9", List.of(), 14L, "tier-test");
        ArgumentCaptor<GenerationTask> captured = ArgumentCaptor.forClass(GenerationTask.class);
        verify(taskMapper).insert(captured.capture());
        GenerationTask task = captured.getValue();
        var recovery = GenerationTaskVO.from(task, json);
        assertThat(recovery.getModelId()).isEqualTo(14L);
        assertThat(recovery.getBeanPerImage()).isEqualTo(15L);
        assertThat(recovery.getPrepaidBeans()).isEqualTo(30L);
        assertThat(json.writeValueAsString(recovery))
                .doesNotContain("modelName", "GPT", "gpt-", "Flare", "provider", "luxai", "modelDeployment");
        // Real names remain available in internal snapshots for retries and support.
        assertThat(task.getModelNameSnapshot()).isEqualTo("GPT Image 2.5 Flare");
        assertThat(task.getModelDeploymentSnapshot()).isEqualTo("gpt-image-2.5-flare");

        task.setModelDeploymentSnapshot(null);
        task.setModelNameSnapshot("GPT Image 2.5 Sunburst (Luxai)");
        assertThat(json.writeValueAsString(GenerationTaskVO.from(task, json))).doesNotContain("Sunburst", "Luxai", "modelName");
        task.setModelNameSnapshot("a future private model name");
        assertThat(json.writeValueAsString(GenerationTaskVO.from(task, json))).doesNotContain("private model", "modelName");
    }

    @Test
    void freezesSelectedModelPriceQualityAndReferenceOrderBeforeDebit() {
        String taskId = service.submitImageGeneration(7L, null, "draw", 2, "2K", "16:9",
                List.of("/profile/second.png", "/profile/first.png"), 14L, "stable-1");
        ArgumentCaptor<GenerationTask> captured = ArgumentCaptor.forClass(GenerationTask.class);
        verify(taskMapper).insert(captured.capture());
        GenerationTask task = captured.getValue();
        model.setBeanPerImage2k(999L);
        model.setImageQuality("low");
        assertThat(task.getModelId()).isEqualTo(14L);
        assertThat(task.getBeanPerImageSnapshot()).isEqualTo(15L);
        assertThat(task.getPrepaidBeans()).isEqualTo(30L);
        assertThat(task.getImageQualitySnapshot()).isEqualTo("high");
        assertThat(task.getRequestId()).isEqualTo("stable-1");
        assertThat(task.getAspectRatio()).isEqualTo("16:9");
        assertThat(task.getRefImages()).isEqualTo("[\"/profile/second.png\",\"/profile/first.png\"]");
        verify(beans).deduct(7L, 30L, "img:" + taskId, "AI出图预扣");
    }

    @Test
    void sameRequestReusesTaskEvenWhenModelPricesOrActivationHaveChanged() {
        GenerationTask existing = new GenerationTask();
        existing.setTaskId("existing-task");
        existing.setUserId(7L);
        existing.setModelId(14L);
        existing.setPrompt("draw");
        existing.setN(2);
        existing.setResolution("2K");
        existing.setAspectRatio("16:9");
        existing.setRequestId("stable-1");
        doReturn(existing).when(service).getOne(any(LambdaQueryWrapper.class));
        model.setIsActive(0);
        model.setBeanPerImage2k(999L);
        assertThat(service.submitImageGeneration(7L, null, "draw", 2, "2K", "16:9", List.of(), 14L, "stable-1"))
                .isEqualTo("existing-task");
        verifyNoInteractions(beans, queue, taskMapper);
    }

    @Test
    void nineImageSubmissionUsesSelectedResolutionAndChargesExactlyOnceOnRetry() {
        String taskId = service.submitImageGeneration(7L, null, "draw nine", 9, "2K", "16:9",
                List.of(), 14L, "nine-images");
        ArgumentCaptor<GenerationTask> captured = ArgumentCaptor.forClass(GenerationTask.class);
        verify(taskMapper).insert(captured.capture());
        GenerationTask saved = captured.getValue();
        assertThat(saved.getN()).isEqualTo(9);
        assertThat(saved.getResolution()).isEqualTo("2K");
        assertThat(saved.getPrepaidBeans()).isEqualTo(135L);
        doReturn(saved).when(service).getOne(any(LambdaQueryWrapper.class));

        assertThat(service.submitImageGeneration(7L, null, "draw nine", 9, "2K", "16:9",
                List.of(), 14L, "nine-images")).isEqualTo(taskId);
        verify(beans, times(1)).deduct(7L, 135L, "img:" + taskId, "AI出图预扣");
        verify(taskMapper, times(1)).insert(any(GenerationTask.class));
        assertThatThrownBy(() -> service.submitImageGeneration(7L, null, "draw nine", 9, "1K", "16:9",
                List.of(), 14L, "nine-images")).isInstanceOf(ServiceException.class);
    }

    @Test
    void unavailableResolutionAndExcessCountDoNotChargeOrQueue() {
        assertThatThrownBy(() -> service.submitImageGeneration(7L, null, "draw", 9, "4K", "16:9",
                List.of(), 14L, "unsupported")).isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> service.submitImageGeneration(7L, null, "draw", 10, "2K", "16:9",
                List.of(), 14L, "too-many")).isInstanceOf(ServiceException.class);
        verifyNoInteractions(beans, queue, taskMapper);
    }

    @Test
    void rejectsCombinedReferencesOverFiftyMegabytesBeforeDebitingOrQueuing() throws Exception {
        when(storage.size("/profile/a.png")).thenReturn(30L * 1024 * 1024);
        when(storage.size("/profile/b.png")).thenReturn(20L * 1024 * 1024 + 1);
        assertThatThrownBy(() -> service.submitImageGeneration(7L, null, "draw", 1, "2K", "16:9",
                List.of("/profile/a.png", "/profile/b.png"), 14L, "too-large"))
                .isInstanceOf(ServiceException.class).hasMessageContaining("50MB");
        verifyNoInteractions(beans, queue, taskMapper);
    }

    @Test
    void acceptsExactlyFiftyMegabytesAndCountsEveryActualReference() throws Exception {
        when(storage.size("/profile/a.png")).thenReturn(25L * 1024 * 1024);
        String taskId = service.submitImageGeneration(7L, null, "draw", 1, "2K", "16:9",
                List.of("/profile/a.png", "/profile/a.png"), 14L, "at-limit");
        verify(beans).deduct(7L, 15L, "img:" + taskId, "AI出图预扣");
        verify(storage, times(1)).size("/profile/a.png");
    }

    @Test
    void batchChecksEachActualSubsetNotUnionOfAllInputs() throws Exception {
        when(storage.size("/profile/a.png")).thenReturn(30L * 1024 * 1024);
        when(storage.size("/profile/b.png")).thenReturn(30L * 1024 * 1024);
        when(storage.size("/profile/c.png")).thenReturn(20L * 1024 * 1024);
        List<GenerateImageItem> items = List.of(imageItem(1, List.of(1, 3)), imageItem(2, List.of(2, 3)));
        String taskId = service.submitImageBatch(7L, 11L, 21L, items, "2K", "16:9",
                List.of("/profile/a.png", "/profile/b.png", "/profile/c.png"), 14L, "valid-subsets");
        verify(beans).deduct(7L, 30L, "img:" + taskId, "AI出图预扣");
        verify(storage, times(1)).size("/profile/c.png");
    }

    @Test
    void oneOversizedBatchItemRejectsWholeNewBatchBeforeAnyDebit() throws Exception {
        when(storage.size("/profile/a.png")).thenReturn(30L * 1024 * 1024);
        when(storage.size("/profile/b.png")).thenReturn(21L * 1024 * 1024);
        List<GenerateImageItem> items = List.of(imageItem(1, List.of(1)), imageItem(2, List.of(1, 2)));
        assertThatThrownBy(() -> service.submitImageBatch(7L, 11L, 21L, items, "2K", "16:9",
                List.of("/profile/a.png", "/profile/b.png"), 14L, "invalid-subset"))
                .hasMessageContaining("50MB");
        verifyNoInteractions(beans, queue, taskMapper);
    }

    @Test
    void existingStandaloneRequestReplaysWithoutReadingDeletedReferences() throws Exception {
        GenerationTask existing = new GenerationTask();
        existing.setTaskId("old-task"); existing.setModelId(14L); existing.setPrompt("draw");
        existing.setN(1); existing.setResolution("2K"); existing.setAspectRatio("16:9");
        existing.setRefImages("[\"/profile/removed.png\"]");
        doReturn(existing).when(service).getOne(any(LambdaQueryWrapper.class));
        when(storage.size(anyString())).thenThrow(new ServiceException("deleted"));
        assertThat(service.submitImageGeneration(7L, null, "draw", 1, "2K", "16:9",
                List.of("/profile/removed.png"), 14L, "old-request")).isEqualTo("old-task");
        verifyNoInteractions(storage, beans, queue, taskMapper);
    }

    @Test
    void existingBatchRequestReplaysWithoutReadingDeletedReferencesOrChargingAgain() throws Exception {
        List<GenerateImageItem> items = List.of(imageItem(1, List.of(1)));
        List<String> refs = List.of("/profile/removed.png");
        String taskId = service.submitImageBatch(7L, 11L, 21L, items, "2K", "16:9", refs, 14L, "batch-replay");
        ArgumentCaptor<GenerationTask> captured = ArgumentCaptor.forClass(GenerationTask.class);
        verify(taskMapper).insert(captured.capture());
        doReturn(captured.getValue()).when(service).getOne(any(LambdaQueryWrapper.class));
        when(storage.size(anyString())).thenThrow(new ServiceException("deleted"));
        clearInvocations(storage, beans, queue, taskMapper);
        assertThat(service.submitImageBatch(7L, 11L, 21L, items, "2K", "16:9", refs, 14L, "batch-replay"))
                .isEqualTo(taskId);
        verifyNoInteractions(storage, beans, queue, taskMapper);
    }

    @Test
    void otherProvidersKeepTheirExistingReferenceBehavior() {
        model.setProvider("yunwu");
        service.submitImageGeneration(7L, null, "draw", 1, "2K", "16:9",
                List.of("https://example.invalid/external.png"), 14L, "other-provider");
        verifyNoInteractions(storage);
        verify(beans).deduct(eq(7L), eq(15L), anyString(), eq("AI出图预扣"));
    }

    @Test
    void externalNonProfileReferencesAndMissingFilesAreRejectedWithoutFetchingOrCharging() throws Exception {
        assertThatThrownBy(() -> service.submitImageGeneration(7L, null, "draw", 1, "2K", "16:9",
                List.of("https://example.invalid/external.png"), 14L, "external"))
                .hasMessageContaining("参考图地址无效");
        verifyNoInteractions(storage);
        when(storage.size("/profile/missing.png")).thenThrow(new java.io.IOException("missing"));
        assertThatThrownBy(() -> service.submitImageGeneration(7L, null, "draw", 1, "2K", "16:9",
                List.of("/profile/missing.png"), 14L, "missing"))
                .hasMessageContaining("重新上传");
        verifyNoInteractions(beans, queue, taskMapper);
    }

    private GenerateImageItem imageItem(int seq, List<Integer> references) {
        GenerateImageItem item = new GenerateImageItem();
        item.setSeq(seq); item.setPrompt("draw " + seq); item.setReferenceImageIndexes(references);
        return item;
    }

    @Test
    void requestIdCannotBeReusedForChangedPromptAndDisabledModelsCannotBeSelected() {
        GenerationTask existing = new GenerationTask();
        existing.setPrompt("original");
        existing.setN(1);
        doReturn(existing).when(service).getOne(any(LambdaQueryWrapper.class));
        assertThatThrownBy(() -> service.submitImageGeneration(7L, null, "changed", 1, null, null, List.of(), 14L, "stable-1"))
                .isInstanceOf(ServiceException.class).hasMessageContaining("不同的出图内容");
        doReturn(null).when(service).getOne(any(LambdaQueryWrapper.class));
        model.setIsActive(0);
        assertThatThrownBy(() -> service.submitImageGeneration(7L, null, "draw", 1, null, null, List.of(), 14L, "stable-2"))
                .isInstanceOf(ServiceException.class).hasMessageContaining("未启用");
        verifyNoInteractions(beans, queue, taskMapper);
    }
}
