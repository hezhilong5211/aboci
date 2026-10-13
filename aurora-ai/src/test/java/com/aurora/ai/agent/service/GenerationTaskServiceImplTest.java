package com.aurora.ai.agent.service;

import com.aurora.ai.agent.billing.BillingCalculator;
import com.aurora.ai.agent.billing.IBeanDeductPort;
import com.aurora.ai.agent.domain.GenerationTask;
import com.aurora.ai.agent.dto.GenerateImageItem;
import com.aurora.ai.agent.dto.ImageReferenceBinding;
import com.aurora.ai.agent.dto.WorkBatchDetailVO;
import com.aurora.ai.agent.dto.WorkItemDetailVO;
import com.aurora.ai.agent.dto.WorkVO;
import com.aurora.ai.agent.service.impl.GenerationTaskServiceImpl;
import com.aurora.ai.domain.SysAiModel;
import com.aurora.ai.mapper.SysAiModelMapper;
import com.aurora.common.core.exception.ServiceException;
import com.aurora.framework.mq.TaskProducer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GenerationTaskServiceImplTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SysAiModelMapper modelMapper = mock(SysAiModelMapper.class);
    private final IBeanDeductPort beanDeductPort = mock(IBeanDeductPort.class);
    private final TaskProducer taskProducer = mock(TaskProducer.class);
    private GenerationTaskServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), GenerationTask.class);
        service = spy(new GenerationTaskServiceImpl(
                objectMapper, taskProducer, modelMapper, new BillingCalculator()));
        ReflectionTestUtils.setField(service, "beanDeductPort", beanDeductPort);
    }

    @Test
    void shouldValidatePromptAtGptImageBoundary() {
        ReflectionTestUtils.invokeMethod(service, "validatePrompt", "图".repeat(32000));

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                service, "validatePrompt", "图".repeat(32001)))
                .isInstanceOf(ServiceException.class)
                .hasMessage("提示词最多32000个字符");
    }

    @Test
    void shouldFreezeV5ReferencesAndRejectChangedOrderBeforeAnotherSubmission() throws Exception {
        when(modelMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(imageModel());
        when(beanDeductPort.getBalance(7L)).thenReturn(1000L);
        GenerationTask queuedTask = new GenerationTask();
        queuedTask.setId(88L);
        doReturn(queuedTask).when(service).createTask(anyString(), eq(7L), eq(33L), eq(21L), eq(9L),
                anyString(), eq(1), eq("2K"), eq("3:4"), anyString(), anyString());
        doNothing().when(service).markStatus(anyString(), eq("QUEUED"), isNull(), isNull(), isNull());
        GenerateImageItem item = new GenerateImageItem();
        item.setSeq(1);
        item.setContractVersion(5);
        item.setPlannedPrompt("[[ref:clothes]]提供白色上衣，[[ref:scene]]提供窗边环境。");
        item.setReferenceAttachmentIds(List.of("unused", "scene", "clothes"));
        item.setReferenceImageIndexes(List.of(3, 2));
        item.setReferenceBindings(List.of(
                new ImageReferenceBinding("original", "scene", List.of("环境"), "primary"),
                new ImageReferenceBinding("original", "clothes", List.of("服装"), "primary")));
        item.setVisibleTexts(List.of("图2：新品"));
        item.setPrompt(ImagePromptCompiler.compileV5(item.getPlannedPrompt(), item.getReferenceBindings(),
                item.getReferenceAttachmentIds(), item.getReferenceImageIndexes(), item.getVisibleTexts()));
        List<String> refs = List.of("/profile/unused.jpg", "/profile/scene.jpg", "/profile/clothes.jpg");
        service.submitImageBatch(7L, 33L, 21L, List.of(item), null, "3:4", refs);

        ArgumentCaptor<String> snapshot = ArgumentCaptor.forClass(String.class);
        verify(service).createTask(anyString(), eq(7L), eq(33L), eq(21L), eq(9L), eq(item.getPrompt()),
                eq(1), eq("2K"), eq("3:4"), anyString(), snapshot.capture());
        JsonNode stored = objectMapper.readTree(snapshot.getValue()).get(0);
        assertThat(stored.path("contractVersion").asInt()).isEqualTo(5);
        assertThat(stored.path("refImages").toString()).isEqualTo("[\"/profile/clothes.jpg\",\"/profile/scene.jpg\"]");
        assertThat(stored.path("prompt").asText()).contains("本任务输入图1提供白色上衣，本任务输入图2提供窗边环境");
        assertThat(stored.path("plannedPrompt").asText()).contains("[[ref:clothes]]");

        item.setReferenceImageIndexes(List.of(2, 3));
        assertThatThrownBy(() -> service.submitImageBatch(7L, 33L, 21L, List.of(item), null, "3:4", refs))
                .isInstanceOf(ServiceException.class).hasMessageContaining("执行提示词与实际图片引用不一致");
        verify(taskProducer).send("generation-task", Map.of("generationTaskId", 88L));
    }

    @Test
    void shouldPersistSourceMessageWhenAgentSubmitsImageBatch() {
        when(modelMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(imageModel());
        when(beanDeductPort.getBalance(7L)).thenReturn(1000L);
        GenerationTask queuedTask = new GenerationTask();
        queuedTask.setId(88L);
        doReturn(queuedTask).when(service).createTask(
                anyString(), eq(7L), eq(33L), eq(21L), eq(9L), eq("第一张提示词"), eq(1),
                eq("2K"), eq("3:4"), isNull(), anyString());
        doNothing().when(service).markStatus(anyString(), eq("QUEUED"), isNull(), isNull(), isNull());

        GenerateImageItem item = new GenerateImageItem();
        item.setSeq(1);
        item.setPrompt("第一张提示词");
        item.setReferenceImageIndexes(List.of());
        item.setGeneratedReferenceSeqs(List.of());

        String taskId = service.submitImageBatch(
                7L, 33L, 21L, List.of(item), null, "3:4", List.of());

        verify(service).createTask(
                eq(taskId), eq(7L), eq(33L), eq(21L), eq(9L), eq("第一张提示词"), eq(1),
                eq("2K"), eq("3:4"), isNull(), anyString());
        verify(taskProducer).send("generation-task", Map.of("generationTaskId", 88L));
    }

    @Test
    void shouldPersistIndependentOriginalReferenceSubsets() throws Exception {
        when(modelMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(imageModel());
        when(beanDeductPort.getBalance(7L)).thenReturn(2000L);
        GenerationTask queuedTask = new GenerationTask();
        queuedTask.setId(88L);
        doReturn(queuedTask).when(service).createTask(
                anyString(), eq(7L), eq(33L), eq(21L), eq(9L), eq("第1张"), eq(4),
                eq("2K"), eq("3:4"), anyString(), anyString());
        doNothing().when(service).markStatus(anyString(), eq("QUEUED"), isNull(), isNull(), isNull());

        List<GenerateImageItem> items = java.util.stream.IntStream.rangeClosed(1, 4)
                .mapToObj(seq -> {
                    GenerateImageItem item = new GenerateImageItem();
                    item.setSeq(seq);
                    item.setPrompt("第" + seq + "张");
                    item.setReferenceImageIndexes(seq == 4 ? List.of(2, 3) : List.of(seq));
                    item.setGeneratedReferenceSeqs(List.of());
                    return item;
                })
                .toList();

        service.submitImageBatch(
                7L, 33L, 21L, items, null, "3:4",
                List.of("/profile/hair.png", "/profile/scene.png", "/profile/clothes.png"));

        ArgumentCaptor<String> requestItemsCaptor = ArgumentCaptor.forClass(String.class);
        verify(service).createTask(
                anyString(), eq(7L), eq(33L), eq(21L), eq(9L), eq("第1张"), eq(4),
                eq("2K"), eq("3:4"), anyString(), requestItemsCaptor.capture());
        JsonNode fourth = objectMapper.readTree(requestItemsCaptor.getValue()).get(3);
        assertThat(fourth.has("generatedReferenceSeqs")).isFalse();
        assertThat(fourth.path("referenceImageIndexes").toString()).isEqualTo("[2,3]");
        assertThat(fourth.path("refImages").toString())
                .isEqualTo("[\"/profile/scene.png\",\"/profile/clothes.png\"]");
    }

    @Test
    void shouldPrepayFourOneKImagesWhenFiftyBeansAreAvailable() {
        SysAiModel model = imageModel();
        model.setDefaultResolution("1K");
        model.setBeanPerImage1k(6L);
        when(modelMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(model);
        when(beanDeductPort.getBalance(7L)).thenReturn(50L);
        GenerationTask queuedTask = new GenerationTask();
        queuedTask.setId(88L);
        doReturn(queuedTask).when(service).createTask(
                anyString(), eq(7L), eq(33L), eq(21L), eq(9L), eq("第1张"), eq(4),
                eq("1K"), isNull(), isNull(), anyString());
        doNothing().when(service).markStatus(anyString(), eq("QUEUED"), isNull(), isNull(), isNull());

        List<GenerateImageItem> items = java.util.stream.IntStream.rangeClosed(1, 4)
                .mapToObj(seq -> {
                    GenerateImageItem item = new GenerateImageItem();
                    item.setSeq(seq);
                    item.setPrompt("第" + seq + "张");
                    item.setReferenceImageIndexes(List.of());
                    item.setGeneratedReferenceSeqs(List.of());
                    return item;
                })
                .toList();

        String taskId = service.submitImageBatch(
                7L, 33L, 21L, items, null, null, List.of());

        verify(beanDeductPort).deduct(7L, 24L, "img:" + taskId, "AI出图预扣");
        verify(taskProducer).send("generation-task", Map.of("generationTaskId", 88L));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void shouldScopeRecoveredTasksToOwnerConversationAndMessage() {
        GenerationTask task = baseTask();
        doReturn(List.of(task)).when(service).list(any(LambdaQueryWrapper.class));

        List<GenerationTask> result = service.listBySourceMessage(7L, 33L, 21L);

        ArgumentCaptor<LambdaQueryWrapper> wrapperCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(service).list(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment())
                .contains("user_id", "conversation_id", "message_id", "create_time");
        assertThat(result).containsExactly(task);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void shouldBatchHistoricalTasksByTaskId() {
        GenerationTask first = baseTask();
        first.setTaskId("task-a");
        GenerationTask second = baseTask();
        second.setTaskId("task-b");
        doReturn(List.of(first, second)).when(service).list(any(LambdaQueryWrapper.class));

        Map<String, GenerationTask> result = service.mapByTaskIds(
                List.of("task-a", "task-b", "task-a", "", " "));

        ArgumentCaptor<LambdaQueryWrapper> wrapperCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(service).list(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment()).contains("task_id", "IN");
        assertThat(result).containsOnlyKeys("task-a", "task-b");
    }

    @Test
    void shouldRestoreLegacyTaskPromptReferencesAndParameters() {
        GenerationTask task = baseTask();
        task.setPrompt("原始提示词");
        task.setRefImages("[\"/profile/ref-1.png\",\"/profile/ref-2.png\"]");
        task.setResultUrls("[\"/profile/result-1.png\"]");
        doReturn(task).when(service).getOwned("source-task", 7L);
        when(modelMapper.selectById(9L)).thenReturn(imageModel());

        WorkItemDetailVO detail = service.getWorkItemDetail(7L, "source-task", 1);

        assertThat(detail.getPrompt()).isEqualTo("原始提示词");
        assertThat(detail.getReferenceImages())
                .containsExactly("/profile/ref-1.png", "/profile/ref-2.png");
        assertThat(detail.getMode()).isEqualTo("img2img");
        assertThat(detail.getImageUrl()).isEqualTo("/profile/result-1.png");
        assertThat(detail.getResolution()).isEqualTo("2K");
        assertThat(detail.getAspectRatio()).isEqualTo("3:4");
        assertThat(detail.getBeanCost()).isEqualTo(300L);
        assertThat(detail.getCreateTime()).isEqualTo("2026-07-14 12:30:45");
    }

    @Test
    void shouldResolveGeneratedReferencesByOriginalResultSlot() {
        GenerationTask task = batchTask();
        doReturn(task).when(service).getOwned("source-task", 7L);
        when(modelMapper.selectById(9L)).thenReturn(imageModel());

        WorkItemDetailVO detail = service.getWorkItemDetail(7L, "source-task", 3);

        assertThat(detail.getPrompt()).isEqualTo("第三张提示词");
        assertThat(detail.getReferenceImages())
                .containsExactly("/profile/result-1.png", "/profile/user-ref.png");
        assertThat(detail.getImageUrl()).isNull();
    }

    @Test
    void shouldUseHistoricalSettledCostForSuccessfulWorkItem() {
        GenerationTask task = batchTask();
        task.setSuccessCount(2);
        task.setBeanCost(400);
        doReturn(task).when(service).getOwned("source-task", 7L);
        when(modelMapper.selectById(9L)).thenReturn(imageModel());

        WorkItemDetailVO detail = service.getWorkItemDetail(7L, "source-task", 1);

        assertThat(detail.getBeanCost()).isEqualTo(200L);
    }

    @Test
    void shouldBuildCreatePageBatchDetailWithFailedSlotAndReferences() {
        GenerationTask task = baseTask();
        task.setConversationId(null);
        task.setN(2);
        task.setPrompt("生成页原始提示词");
        task.setRefImages("[\"/profile/ref-1.png\"]");
        task.setResultUrls("[\"/profile/result-1.png\",\"\"]");
        task.setSuccessCount(1);
        task.setBeanCost(300);
        doReturn(task).when(service).getOwned("source-task", 7L);

        WorkBatchDetailVO detail = service.getWorkBatchDetail(7L, "source-task");

        assertThat(detail.getSourceType()).isEqualTo("CREATE_PAGE");
        assertThat(detail.getBatchPrompt()).isEqualTo("生成页原始提示词");
        assertThat(detail.getPlannedCount()).isEqualTo(2);
        assertThat(detail.getSuccessCount()).isEqualTo(1);
        assertThat(detail.getFailedCount()).isEqualTo(1);
        assertThat(detail.getTotalBeanCost()).isEqualTo(300L);
        assertThat(detail.getItems()).hasSize(2);
        assertThat(detail.getItems().get(0).getStatus()).isEqualTo("SUCCEEDED");
        assertThat(detail.getItems().get(0).getFullPrompt()).isEqualTo("生成页原始提示词");
        assertThat(detail.getItems().get(0).getReferences()).singleElement().satisfies(reference -> {
            assertThat(reference.getIndex()).isEqualTo(1);
            assertThat(reference.getSourceType()).isEqualTo("REFERENCE");
            assertThat(reference.getUrl()).isEqualTo("/profile/ref-1.png");
        });
        assertThat(detail.getItems().get(1).getStatus()).isEqualTo("FAILED");
        assertThat(detail.getItems().get(1).getBeanCost()).isZero();
    }

    @Test
    void shouldRestoreAgentTargetsOriginalPromptAndGeneratedReferences() throws Exception {
        GenerationTask task = baseTask();
        task.setN(3);
        task.setResultUrls("[\"/profile/result-1.png\",\"/profile/result-2.png\",\"\"]");
        task.setSuccessCount(2);
        task.setBeanCost(600);
        task.setRequestItems("""
                [
                  {"seq":1,"prompt":%s,"refImages":[],"generatedReferenceSeqs":[]},
                  {"seq":2,"prompt":%s,"refImages":[],"generatedReferenceSeqs":[]},
                  {"seq":3,"prompt":%s,"refImages":["/profile/user-ref.png"],"generatedReferenceSeqs":[1]}
                ]
                """.formatted(
                objectMapper.writeValueAsString(agentPrompt("第一张执行目标", "用户要求生成三张配套图片")),
                objectMapper.writeValueAsString(agentPrompt("第二张执行目标", "用户要求生成三张配套图片")),
                objectMapper.writeValueAsString(agentPrompt("第三张执行目标", "用户要求生成三张配套图片"))));
        doReturn(task).when(service).getOwned("source-task", 7L);

        WorkBatchDetailVO detail = service.getWorkBatchDetail(7L, "source-task");

        assertThat(detail.getSourceType()).isEqualTo("AGENT");
        assertThat(detail.getBatchPrompt()).isEqualTo("用户要求生成三张配套图片");
        assertThat(detail.getItems()).extracting("executionTarget")
                .containsExactly("第一张执行目标", "第二张执行目标", "第三张执行目标");
        assertThat(detail.getItems().get(2).getUserPrompt()).isEqualTo("用户要求生成三张配套图片");
        assertThat(detail.getItems().get(2).getFullPrompt()).contains("【执行边界】");
        assertThat(detail.getItems().get(2).getReferences()).hasSize(2);
        assertThat(detail.getItems().get(2).getReferences().get(0).getSourceType()).isEqualTo("GENERATED");
        assertThat(detail.getItems().get(2).getReferences().get(0).getSourceSeq()).isEqualTo(1);
        assertThat(detail.getItems().get(2).getReferences().get(0).getUrl())
                .isEqualTo("/profile/result-1.png");
        assertThat(detail.getItems().get(2).getReferences().get(1).getSourceType()).isEqualTo("UPLOADED");
        assertThat(detail.getItems().get(2).getStatus()).isEqualTo("FAILED");
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void shouldReturnCompactBatchSummaryForMyWorks() throws Exception {
        GenerationTask task = baseTask();
        task.setId(12L);
        task.setN(3);
        task.setResultUrls("[\"/profile/generated/result-1.png\",\"/profile/generated/result-2.png\",\"\"]");
        task.setRequestItems("""
                [
                  {"seq":1,"prompt":%s,"refImages":[],"generatedReferenceSeqs":[]},
                  {"seq":2,"prompt":%s,"refImages":[],"generatedReferenceSeqs":[]},
                  {"seq":3,"prompt":%s,"refImages":["/profile/ref.png"],"generatedReferenceSeqs":[]}
                ]
                """.formatted(
                objectMapper.writeValueAsString(agentPrompt("第一张目标", "很长的用户原始要求")),
                objectMapper.writeValueAsString(agentPrompt("第二张目标", "很长的用户原始要求")),
                objectMapper.writeValueAsString(agentPrompt("第三张目标", "很长的用户原始要求"))));
        Page<GenerationTask> taskPage = new Page<>(1, 20);
        taskPage.setRecords(List.of(task));
        taskPage.setTotal(1);
        doReturn(taskPage).when(service).page(any(Page.class), any(LambdaQueryWrapper.class));

        IPage<WorkVO> result = service.pageMyWorks(7L, 1, 20, null);

        ArgumentCaptor<LambdaQueryWrapper> wrapperCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(service).page(any(Page.class), wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment()).doesNotContain("conversation_id");

        assertThat(result.getRecords()).singleElement().satisfies(work -> {
            assertThat(work.getSourceType()).isEqualTo("AGENT");
            assertThat(work.getPromptSummary()).isEqualTo("第一张目标；第二张目标 等3张");
            assertThat(work.getPrompt()).isEqualTo(work.getPromptSummary());
            assertThat(work.getPlannedCount()).isEqualTo(3);
            assertThat(work.getSuccessCount()).isEqualTo(2);
            assertThat(work.getFailedCount()).isEqualTo(1);
            assertThat(work.getHasReferences()).isTrue();
            assertThat(work.getPreviewUrls()).containsExactly(
                    "/profile/generated/result-1.preview.jpg",
                    "/profile/generated/result-2.preview.jpg",
                    "");
        });
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void shouldFilterCreatePageWorksAtDatabaseQuery() {
        Page<GenerationTask> taskPage = new Page<>(1, 20);
        doReturn(taskPage).when(service).page(any(Page.class), any(LambdaQueryWrapper.class));

        service.pageMyWorks(7L, 1, 20, "CREATE_PAGE");

        ArgumentCaptor<LambdaQueryWrapper> wrapperCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(service).page(any(Page.class), wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment()).contains("conversation_id IS NULL");
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void shouldFilterAgentWorksAtDatabaseQuery() {
        Page<GenerationTask> taskPage = new Page<>(1, 20);
        doReturn(taskPage).when(service).page(any(Page.class), any(LambdaQueryWrapper.class));

        service.pageMyWorks(7L, 1, 20, "agent");

        ArgumentCaptor<LambdaQueryWrapper> wrapperCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(service).page(any(Page.class), wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment()).contains("conversation_id IS NOT NULL");
    }

    @Test
    void shouldRejectUnknownWorkSource() {
        assertThatThrownBy(() -> service.pageMyWorks(7L, 1, 20, "UNKNOWN"))
                .isInstanceOf(ServiceException.class)
                .hasMessage("作品来源参数无效");
        verify(service, never()).page(any(Page.class), any(LambdaQueryWrapper.class));
    }

    @Test
    void shouldReplayOnlyRequestedFailedSlotAndDeductOneImage() throws Exception {
        GenerationTask task = independentBatchTask();
        doReturn(task).when(service).getOwned("source-task", 7L);
        when(modelMapper.selectById(9L)).thenReturn(imageModel());
        when(beanDeductPort.getBalance(7L)).thenReturn(1000L);
        GenerationTask replayTask = new GenerationTask();
        replayTask.setId(88L);
        doReturn(replayTask).when(service).createTask(
                anyString(), eq(7L), eq(33L), isNull(), eq(9L), eq("第三张提示词"), eq(1),
                eq("2K"), eq("3:4"), anyString(), isNull());
        doNothing().when(service).markStatus(anyString(), eq("QUEUED"), isNull(), isNull(), isNull());

        String replayTaskId = service.replayWorkItem(7L, "source-task", 3, true);

        assertThat(replayTaskId).hasSize(32);
        ArgumentCaptor<String> taskId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> references = ArgumentCaptor.forClass(String.class);
        verify(service).createTask(
                taskId.capture(), eq(7L), eq(33L), isNull(), eq(9L), eq("第三张提示词"), eq(1),
                eq("2K"), eq("3:4"), references.capture(), isNull());
        assertThat(taskId.getValue()).isEqualTo(replayTaskId);
        assertThat(objectMapper.readValue(references.getValue(), String[].class))
                .containsExactly("/profile/user-ref.png");
        verify(beanDeductPort).deduct(7L, 300L, "img:" + replayTaskId, "AI单张重新生成预扣");
        verify(taskProducer).send("generation-task", Map.of("generationTaskId", 88L));
    }

    @Test
    void shouldRejectRetryForSuccessfulSlot() {
        GenerationTask task = batchTask();
        doReturn(task).when(service).getOwned("source-task", 7L);

        assertThatThrownBy(() -> service.replayWorkItem(7L, "source-task", 1, true))
                .isInstanceOf(ServiceException.class)
                .hasMessage("该图片已经生成成功，无需重试");
        verify(service, never()).createTask(
                anyString(), eq(7L), eq(33L), isNull(), eq(9L), anyString(), anyInt(),
                anyString(), anyString(), anyString(), isNull());
        verify(beanDeductPort, never()).deduct(anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    void shouldNotCreateOrQueueTaskWhenAtomicDeductFails() {
        GenerationTask task = independentBatchTask();
        doReturn(task).when(service).getOwned("source-task", 7L);
        when(modelMapper.selectById(9L)).thenReturn(imageModel());
        when(beanDeductPort.getBalance(7L)).thenReturn(1000L);
        doThrow(new ServiceException("豆子余额不足")).when(beanDeductPort)
                .deduct(eq(7L), eq(300L), anyString(), eq("AI单张重新生成预扣"));

        assertThatThrownBy(() -> service.replayWorkItem(7L, "source-task", 3, true))
                .isInstanceOf(ServiceException.class)
                .hasMessage("豆子余额不足");
        verify(service, never()).createTask(
                anyString(), eq(7L), eq(33L), isNull(), eq(9L), anyString(), anyInt(),
                anyString(), anyString(), anyString(), isNull());
        verify(taskProducer, never()).send(anyString(), org.mockito.ArgumentMatchers.<Map<String, Object>>any());
    }

    @Test
    void shouldFailTaskAndRefundWhenQueuePublishingFails() {
        GenerationTask task = independentBatchTask();
        doReturn(task).when(service).getOwned("source-task", 7L);
        when(modelMapper.selectById(9L)).thenReturn(imageModel());
        when(beanDeductPort.getBalance(7L)).thenReturn(1000L);
        GenerationTask replayTask = new GenerationTask();
        replayTask.setId(88L);
        doReturn(replayTask).when(service).createTask(
                anyString(), eq(7L), eq(33L), isNull(), eq(9L), eq("第三张提示词"), eq(1),
                eq("2K"), eq("3:4"), anyString(), isNull());
        doNothing().when(service).markStatus(anyString(), anyString(), isNull(), anyInt(), anyString());
        doNothing().when(service).markStatus(anyString(), eq("QUEUED"), isNull(), isNull(), isNull());
        doThrow(new RuntimeException("队列不可用")).when(taskProducer)
                .send(eq("generation-task"), org.mockito.ArgumentMatchers.<Map<String, Object>>any());

        assertThatThrownBy(() -> service.replayWorkItem(7L, "source-task", 3, true))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("队列不可用");

        ArgumentCaptor<String> bizNo = ArgumentCaptor.forClass(String.class);
        verify(beanDeductPort).deduct(eq(7L), eq(300L), bizNo.capture(), eq("AI单张重新生成预扣"));
        verify(beanDeductPort).refund(7L, 300L, bizNo.getValue(), "任务提交失败退还");
        verify(service).markStatus(bizNo.getValue().substring(4), "FAILED", null, 0, "任务提交失败");
    }

    @Test
    void shouldRejectUnknownOwnerAndOutOfRangeSequence() {
        doReturn(null).when(service).getOwned("missing-task", 7L);

        assertThatThrownBy(() -> service.getWorkItemDetail(7L, "missing-task", 1))
                .isInstanceOf(ServiceException.class)
                .hasMessage("任务不存在或无权访问");

        GenerationTask task = baseTask();
        doReturn(task).when(service).getOwned("source-task", 7L);
        assertThatThrownBy(() -> service.getWorkItemDetail(7L, "source-task", 2))
                .isInstanceOf(ServiceException.class)
                .hasMessage("图片序号不存在");

        assertThatThrownBy(() -> service.getWorkBatchDetail(7L, "missing-task"))
                .isInstanceOf(ServiceException.class)
                .hasMessage("任务不存在或无权访问");
    }

    private GenerationTask baseTask() {
        GenerationTask task = new GenerationTask();
        task.setTaskId("source-task");
        task.setUserId(7L);
        task.setConversationId(33L);
        task.setModelId(9L);
        task.setN(1);
        task.setResolution("2K");
        task.setAspectRatio("3:4");
        task.setStatus("SUCCEEDED");
        task.setCreateTime(LocalDateTime.of(2026, 7, 14, 12, 30, 45));
        return task;
    }

    private GenerationTask independentBatchTask() {
        GenerationTask task = batchTask();
        task.setRequestItems(task.getRequestItems().replace("\"generatedReferenceSeqs\":[1]", "\"generatedReferenceSeqs\":[]"));
        return task;
    }

    @Test
    void shouldRejectLegacyDependencyReplayBeforeChargingAndKeepHistoryReadable() {
        GenerationTask task = batchTask();
        doReturn(task).when(service).getOwned("source-task", 7L);
        assertThat(service.getWorkItemDetail(7L, "source-task", 3).getReferenceImages())
                .containsExactly("/profile/result-1.png", "/profile/user-ref.png");
        assertThat(service.getWorkItemDetail(7L, "source-task", 3).isReplaySupported()).isFalse();
        assertThatThrownBy(() -> service.replayWorkItem(7L, "source-task", 3, true))
                .isInstanceOf(ServiceException.class).hasMessageContaining("不支持任务内生成图引用");
        verify(beanDeductPort, never()).deduct(anyLong(), anyLong(), anyString(), anyString());
        verify(taskProducer, never()).send(anyString(), org.mockito.ArgumentMatchers.<Map<String, Object>>any());
    }

    private GenerationTask batchTask() {
        GenerationTask task = baseTask();
        task.setN(3);
        task.setResultUrls("[\"/profile/result-1.png\",\"/profile/result-2.png\",\"\"]");
        task.setRequestItems("""
                [
                  {"seq":1,"prompt":"第一张提示词","refImages":[],"generatedReferenceSeqs":[]},
                  {"seq":2,"prompt":"第二张提示词","refImages":[],"generatedReferenceSeqs":[]},
                  {"seq":3,"prompt":"第三张提示词","refImages":["/profile/user-ref.png"],"generatedReferenceSeqs":[1]}
                ]
                """);
        return task;
    }

    private SysAiModel imageModel() {
        SysAiModel model = new SysAiModel();
        model.setId(9L);
        model.setProvider("yunwu");
        model.setBeanPerImage2k(300L);
        model.setDefaultResolution("2K");
        return model;
    }

    private String agentPrompt(String target, String original) {
        return "【本张唯一执行目标】\n" + target
                + "\n\n【完整用户原始要求（仅作为约束背景）】\n" + original
                + "\n\n【执行边界】只生成上述唯一目标对应的一张图片。";
    }
}
