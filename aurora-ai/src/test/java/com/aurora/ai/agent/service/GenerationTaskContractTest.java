package com.aurora.ai.agent.service;

import com.aurora.ai.agent.billing.BillingCalculator;
import com.aurora.ai.agent.billing.IBeanDeductPort;
import com.aurora.ai.agent.domain.GenerationTask;
import com.aurora.ai.agent.dto.GenerateImageItem;
import com.aurora.ai.agent.dto.ImageReferenceBinding;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GenerationTaskContractTest {

    private static final List<String> ATTACHMENT_IDS = List.of(
            "face-attachment", "hair-attachment", "outfit-attachment", "salon-attachment", "outdoor-attachment");
    private static final List<String> REFERENCE_URLS = List.of(
            "/profile/face.png", "/profile/hair.png", "/profile/outfit.png",
            "/profile/salon.png", "/profile/outdoor.png");
    private static final String SOURCE_PROMPT = """
             【原始要求】
            生成2张独立照片。图1提供人物，图2提供发型，图3提供服装，图4提供室内场景，图5提供室外场景。
            原先的人物外观比较年轻，需要确认是否维持原年龄；每张保持真实皮肤和自然光线，不增加耳环。

            【客户补充】
            保留主要五官，统一表现为53岁；改造前仍为同年龄、同一人物。
            改造后的室外主图与改造前的室内照片分别输出，不拼接。
            """;
    private static final String EFFECTIVE_REQUEST = "最终输出2张：53岁女性的室外改造后主图及室内改造前照片。"
            + "同一人物、同一年龄、同一服装；改造前只改变头发状态及场景，不戴耳环。";
    private static final String REQUEST_UPDATE = "保留主要五官，统一表现为53岁；改造前仍为同年龄、同一人物。";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SysAiModelMapper modelMapper = mock(SysAiModelMapper.class);
    private final GenerationTaskMapper taskMapper = mock(GenerationTaskMapper.class);
    private final IBeanDeductPort beanDeductPort = mock(IBeanDeductPort.class);
    private final TaskProducer taskProducer = mock(TaskProducer.class);
    private GenerationTaskServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), GenerationTask.class);
        service = new GenerationTaskServiceImpl(objectMapper, taskProducer, modelMapper, new BillingCalculator(), mock(ProfileStorage.class));
        ReflectionTestUtils.setField(service, "baseMapper", taskMapper);
        ReflectionTestUtils.setField(service, "beanDeductPort", beanDeductPort);

        SysAiModel model = new SysAiModel();
        model.setId(9L);
        model.setProvider("yunwu");
        model.setBeanPerImage2k(300L);
        model.setDefaultResolution("2K");
        when(modelMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(model);
        when(beanDeductPort.getBalance(7L)).thenReturn(2000L);
        when(taskMapper.insert(any(GenerationTask.class))).thenAnswer(invocation -> {
            GenerationTask inserted = invocation.getArgument(0);
            inserted.setId(88L);
            return 1;
        });
        when(taskMapper.updateById(any(GenerationTask.class))).thenReturn(1);
    }

    @Test
    void shouldPersistIndependentVersionFourAuditAndActualReferenceOrder() throws Exception {
        List<GenerateImageItem> items = validBatch();

        String taskId = service.submitImageBatch(7L, 33L, 21L, items, null, "3:4", REFERENCE_URLS);

        GenerationTask stored = insertedTask();
        assertThat(stored.getTaskId()).isEqualTo(taskId);
        assertThat(stored.getUserId()).isEqualTo(7L);
        assertThat(stored.getConversationId()).isEqualTo(33L);
        assertThat(stored.getMessageId()).isEqualTo(21L);
        assertThat(stored.getN()).isEqualTo(2);
        assertThat(stored.getPrompt()).isEqualTo(items.getFirst().getPrompt());
        assertThat(objectMapper.readValue(stored.getRefImages(), String[].class))
                .containsExactlyElementsOf(REFERENCE_URLS);

        JsonNode snapshots = objectMapper.readTree(stored.getRequestItems());
        assertThat(snapshots.size()).isEqualTo(2);
        for (int index = 0; index < snapshots.size(); index++) {
            JsonNode item = snapshots.get(index);
            assertThat(item.path("seq").asInt()).isEqualTo(index + 1);
            assertThat(item.path("contractVersion").asInt()).isEqualTo(4);
            assertThat(item.path("sourcePrompt").asText()).isEqualTo(SOURCE_PROMPT);
            assertThat(item.path("effectiveRequest").asText()).isEqualTo(EFFECTIVE_REQUEST);
            assertThat(item.path("requestUpdate").asText()).isEqualTo(REQUEST_UPDATE);
            assertThat(item.path("plannedPrompt").asText()).isEqualTo(items.get(index).getPlannedPrompt());
            assertThat(item.path("referenceAttachmentIds"))
                    .isEqualTo(objectMapper.valueToTree(ATTACHMENT_IDS));
            assertThat(item.path("referenceBindings"))
                    .isEqualTo(objectMapper.valueToTree(items.get(index).getReferenceBindings()));
            assertThat(item.path("prompt").asText())
                    .isEqualTo(items.get(index).getPrompt())
                    .doesNotContain("【原始要求】", "【客户补充】", "图4提供室内场景");
        }

        JsonNode before = snapshots.get(1);
        assertThat(before.has("generatedReferenceSeqs")).isFalse();
        assertThat(before.path("referenceImageIndexes").toString()).isEqualTo("[1,4,3]");
        assertThat(objectMapper.treeToValue(before.path("refImages"), String[].class))
                .containsExactly("/profile/face.png", "/profile/salon.png", "/profile/outfit.png");
        assertThat(before.path("prompt").asText())
                .contains("本任务输入图1（上传素材）：主要依据：人物身份、年龄、体型",
                        "本任务输入图2（上传素材）：主要依据：场景",
                        "本任务输入图3（上传素材）：主要依据：服装")
                .doesNotContain("整体造型一致性");

        verify(beanDeductPort).deduct(7L, 600L, "img:" + taskId, "AI出图预扣");
        verify(taskProducer).send("generation-task", Map.of("generationTaskId", 88L));
    }

    @Test
    void shouldRejectWholeBatchBeforeChargingWhenLaterItemHasTwoPrimaryIdentitySources() {
        List<GenerateImageItem> items = validBatch();
        GenerateImageItem before = items.get(1);
        List<ImageReferenceBinding> conflicting = new ArrayList<>(before.getReferenceBindings());
        conflicting.add(new ImageReferenceBinding("original", "outfit-attachment", List.of("人物身份"), "primary"));
        before.setReferenceBindings(conflicting);

        assertThatThrownBy(() -> service.submitImageBatch(
                7L, 33L, 21L, items, null, "3:4", REFERENCE_URLS))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("多个主要参考来源");
        assertNoTaskWasSubmitted();
    }

    @Test
    void shouldRejectEditedExecutionPromptBeforeChargingOrPersisting() {
        List<GenerateImageItem> items = validBatch();
        GenerateImageItem before = items.get(1);
        before.setPrompt(before.getPrompt().replace("53岁", "45岁"));

        assertThatThrownBy(() -> service.submitImageBatch(
                7L, 33L, 21L, items, null, "3:4", REFERENCE_URLS))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("执行提示词与实际图片引用不一致");
        assertNoTaskWasSubmitted();
    }

    @Test
    void shouldRejectReferenceReorderingWhenPromptStillUsesPreviousInputOrder() {
        List<GenerateImageItem> items = validBatch();
        items.get(1).setReferenceImageIndexes(List.of(1, 3, 4));

        assertThatThrownBy(() -> service.submitImageBatch(
                7L, 33L, 21L, items, null, "3:4", REFERENCE_URLS))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("执行提示词与实际图片引用不一致");
        assertNoTaskWasSubmitted();
    }

    @Test
    void shouldRejectUnsupportedContractVersionBeforeSideEffects() {
        List<GenerateImageItem> items = validBatch();
        items.get(1).setContractVersion(99);

        assertThatThrownBy(() -> service.submitImageBatch(
                7L, 33L, 21L, items, null, "3:4", REFERENCE_URLS))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("契约版本或附件快照无效");
        assertNoTaskWasSubmitted();
    }

    @Test
    void shouldKeepLegacyItemsCompatibleWithoutRecompilingOriginalCoordinates() throws Exception {
        String legacyPrompt = "【旧提示词】\n按图2风格生成第1张照片，保留旧提示词原文。";
        GenerateImageItem legacy = new GenerateImageItem();
        legacy.setSeq(1);
        legacy.setPrompt(legacyPrompt);
        legacy.setReferenceImageIndexes(List.of(2, 1));
        legacy.setGeneratedReferenceSeqs(List.of());

        String taskId = service.submitImageBatch(
                7L, 33L, 21L, List.of(legacy), null, "3:4", REFERENCE_URLS);

        GenerationTask stored = insertedTask();
        assertThat(stored.getPrompt()).isEqualTo(legacyPrompt);
        JsonNode snapshot = objectMapper.readTree(stored.getRequestItems()).get(0);
        assertThat(snapshot.path("prompt").asText()).isEqualTo(legacyPrompt);
        assertThat(snapshot.has("contractVersion")).isFalse();
        assertThat(snapshot.has("referenceBindings")).isFalse();
        assertThat(snapshot.has("sourcePrompt")).isFalse();
        assertThat(objectMapper.treeToValue(snapshot.path("refImages"), String[].class))
                .containsExactly("/profile/hair.png", "/profile/face.png");
        verify(beanDeductPort).deduct(7L, 300L, "img:" + taskId, "AI出图预扣");
        verify(taskProducer).send("generation-task", Map.of("generationTaskId", 88L));
    }

    @Test
    void shouldKeepPreviouslyFrozenVersionThreePromptOnReplay() throws Exception {
        List<GenerateImageItem> items = validBatch();
        items.forEach(item -> {
            item.setContractVersion(3);
            item.setPrompt("旧版本冻结提示词，不随当前编译规则改变");
        });
        service.submitImageBatch(7L, 33L, 21L, items, null, "3:4", REFERENCE_URLS);
        JsonNode snapshots = objectMapper.readTree(insertedTask().getRequestItems());
        assertThat(snapshots.get(0).path("prompt").asText()).isEqualTo("旧版本冻结提示词，不随当前编译规则改变");
        assertThat(snapshots.get(0).path("contractVersion").asInt()).isEqualTo(3);
    }

    private List<GenerateImageItem> validBatch() {
        GenerateImageItem after = contractItem(1,
                "53岁女性的室外正面主图，自然微笑，露耳超短碎发，保持指定服装，不戴耳环。",
                List.of(1, 2, 3, 5), List.of(),
                List.of(
                        new ImageReferenceBinding("original", "face-attachment", List.of("人物身份"), "primary"),
                        new ImageReferenceBinding("original", "hair-attachment", List.of("发型结构"), "primary"),
                        new ImageReferenceBinding("original", "outfit-attachment", List.of("服装"), "primary"),
                        new ImageReferenceBinding("original", "outdoor-attachment", List.of("场景"), "primary")));
        GenerateImageItem before = contractItem(2,
                "同一位53岁女性的室内改造前正面照，齐下巴自然深色头发，贴头皮且未打理，不戴耳环。",
                List.of(1, 4, 3), List.of(),
                List.of(
                        new ImageReferenceBinding("original", "outfit-attachment", List.of("服装"), "primary"),
                        new ImageReferenceBinding("original", "face-attachment", List.of("人物身份", "年龄", "体型"), "primary"),
                        new ImageReferenceBinding("original", "salon-attachment", List.of("场景"), "primary")));
        return List.of(after, before);
    }

    private GenerateImageItem contractItem(int seq, String target, List<Integer> referenceIndexes,
                                           List<Integer> generatedSeqs, List<ImageReferenceBinding> bindings) {
        GenerateImageItem item = new GenerateImageItem();
        item.setSeq(seq);
        item.setContractVersion(4);
        item.setSourcePrompt(SOURCE_PROMPT);
        item.setEffectiveRequest(EFFECTIVE_REQUEST);
        item.setRequestUpdate(REQUEST_UPDATE);
        item.setPlannedPrompt(target);
        item.setReferenceAttachmentIds(ATTACHMENT_IDS);
        item.setReferenceImageIndexes(referenceIndexes);
        item.setGeneratedReferenceSeqs(generatedSeqs);
        item.setReferenceBindings(bindings);
        item.setPrompt(ImagePromptCompiler.compile(target, bindings, ATTACHMENT_IDS, referenceIndexes));
        return item;
    }

    private GenerationTask insertedTask() {
        ArgumentCaptor<GenerationTask> captor = ArgumentCaptor.forClass(GenerationTask.class);
        verify(taskMapper).insert(captor.capture());
        return captor.getValue();
    }

    private void assertNoTaskWasSubmitted() {
        verify(beanDeductPort, never()).deduct(anyLong(), anyLong(), anyString(), anyString());
        verify(beanDeductPort, never()).refund(anyLong(), anyLong(), anyString(), anyString());
        verifyNoInteractions(taskMapper, taskProducer);
    }
}
