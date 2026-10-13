package com.aurora.ai.agent.service;

import com.aurora.ai.agent.billing.BillingCalculator;
import com.aurora.ai.agent.domain.Message;
import com.aurora.ai.agent.domain.MessageAttachment;
import com.aurora.ai.agent.domain.ReferenceScope;
import com.aurora.ai.agent.dto.AgentChatRequest;
import com.aurora.ai.agent.dto.ClaimedReferenceScope;
import com.aurora.ai.agent.dto.GenerateImageItem;
import com.aurora.ai.mapper.SysAiModelMapper;
import com.aurora.ai.provider.ResponsesApiClient;
import com.aurora.framework.mq.ApiRateLimiter;
import com.aurora.framework.storage.ProfileStorage;
import com.aurora.framework.utils.EncryptUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.aurora.common.core.exception.ServiceException;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 只验证附件编号空间、澄清合并和最新原文，不调用供应商或读真实图片。 */
class AgentReferenceContextTest {

    private static final String ORIGINAL = "最终输出6张，人物50-55岁。图1是人物，图2是服装。";
    private static final String REPLACEMENT = "最终输出6张独立图片，人物60-65岁。\n"
            + "图1：发型参考。\n图2：成品环境。\n图3：服装参考。\n"
            + "图4：改造前环境。\n图5：发色参考。";

    @Test
    void shouldReplaceOldScopeWithNewFiveImageNumbering() throws Exception {
        Fixture fixture = new Fixture(4, REPLACEMENT);
        Object context = fixture.resolve(refs("new", 5));
        Object selected = fixture.select(context, "REPLACE");

        assertEquals(ids("new", 5), attachmentIds(selected));
        assertEquals(REPLACEMENT, field(selected, "sourcePrompt"));
        assertEquals(REPLACEMENT, fixture.invoke("currentRequestText", selected));
        Map<?, ?> indexes = field(selected, "indexesByAttachmentId");
        assertEquals(1, indexes.get("new-1"));
        assertEquals(5, indexes.get("new-5"));
        assertNull(indexes.get("old-1"));

        JsonNode job = fixture.mapper.readTree("{\"reference_attachment_ids\":[\"new-3\",\"new-1\"]}");
        assertEquals(List.of(3, 1), fixture.invoke("parseReferenceAttachmentIds", job, selected));
    }

    @Test
    void shouldPreserveOriginalReferencesForTextOnlyAmendment() throws Exception {
        Fixture fixture = new Fixture(4, "只把人物年龄改成60-65岁，其他保持原要求。");
        Object context = fixture.resolve(List.of());
        Object selected = fixture.select(context, "AMEND");

        assertEquals(ids("old", 4), attachmentIds(selected));
        assertEquals(ORIGINAL, field(selected, "sourcePrompt"));
        assertEquals(fixture.current.getContent(), fixture.invoke("currentRequestText", selected));
        String snapshot = fixture.invoke("buildSourceSnapshot", selected);
        assertTrue(snapshot.contains(ORIGINAL));
        assertTrue(snapshot.contains(fixture.current.getContent()));
    }

    @Test
    void shouldKeepReferencesWhenConfirmingWithoutNewImages() throws Exception {
        Fixture fixture = new Fixture(3, "开始");
        Object selected = fixture.select(fixture.resolve(List.of()), "CONFIRM");

        assertEquals(ids("old", 3), attachmentIds(selected));
        assertEquals(ORIGINAL, field(selected, "sourcePrompt"));
        assertEquals("开始", fixture.invoke("currentRequestText", selected));
    }

    @Test
    void shouldRetainNewAttachmentIdWhenReplacementReusesSameUrl() throws Exception {
        Fixture fixture = new Fixture(2, REPLACEMENT);
        List<InputRef> newReferences = List.of(
                new InputRef("new-same", url("old", 2)),
                new InputRef("new-other", url("new", 2)));
        Object selected = fixture.select(fixture.resolve(newReferences), "REPLACE");

        assertEquals(List.of("new-same", "new-other"), attachmentIds(selected));
        assertEquals(List.of(url("old", 2), url("new", 2)), field(selected, "urls"));
        Map<?, ?> indexes = field(selected, "indexesByAttachmentId");
        assertEquals(1, indexes.get("new-same"));
        assertNull(indexes.get("old-2"));
    }

    @Test
    void shouldKeepSameUrlNewAttachmentAddressableDuringAmendment() throws Exception {
        Fixture fixture = new Fixture(1, "补充这张图片作为颜色参考，人物仍保持原图。");
        Object selected = fixture.select(fixture.resolve(
                List.of(new InputRef("new-same", url("old", 1)))), "AMEND");
        JsonNode job = fixture.mapper.readTree("{\"reference_attachment_ids\":[\"new-same\"]}");

        List<?> indexes = fixture.invoke("parseReferenceAttachmentIds", job, selected);
        assertNotNull(indexes, "同URL的新上传附件ID仍是用户本条明确选中的身份，不可被URL去重吞掉");
        assertEquals(1, indexes.size());
    }

    @Test
    void shouldAllowReplacingFiveOldImagesWithFiveNewImagesBeforeApplyingInputLimit() throws Exception {
        Fixture fixture = new Fixture(5, REPLACEMENT);
        Object context = assertDoesNotThrow(() -> fixture.resolve(refs("new", 5)),
                "旧5张+新5张的暂存集合不得阻止完整新图组替换旧图组");
        Object selected = fixture.select(context, "REPLACE");

        assertEquals(ids("new", 5), attachmentIds(selected));
    }

    @Test
    void shouldAllowReplacingAnEntireNineImageSet() throws Exception {
        Fixture fixture = new Fixture(9, "全部改用本条上传的9张商品参考图，生成1张商品海报。");
        Object context = assertDoesNotThrow(() -> fixture.resolve(refs("new", 9)));
        Object selected = fixture.select(context, "REPLACE");

        assertEquals(ids("new", 9), attachmentIds(selected));
        assertEquals(9, ((List<?>) field(selected, "images")).size());
    }

    @Test
    void shouldPreserveLatestAnswerVerbatimEvenWhenAnOptionWasSelected() throws Exception {
        String latest = "  按推荐，但人物改为60-65岁；商品价格为¥199。\n保留标签：新品✨  ";
        Fixture fixture = new Fixture(1, latest);
        fixture.request.setClarificationOptionId("recommended");
        fixture.scope.setContextJson("""
                {"askedKeys":["HARD_CONFLICT"],"pending":{
                  "key":"HARD_CONFLICT","question":"是否按推荐方案继续？",
                  "imageUpdatePolicy":"KEEP_APPEND","recommendedOptionId":"recommended",
                  "options":[{"id":"recommended","label":"按推荐方案处理"}]}}
                """);

        Object context = fixture.resolve(List.of());
        String contextJson = field(context, "clarificationContextJson");
        JsonNode stored = fixture.mapper.readTree(contextJson);
        assertEquals(latest, stored.path("currentPrompt").asText());
        assertEquals(latest, stored.path("answers").get(0).path("answer").asText());
        String snapshot = fixture.invoke("buildSourceSnapshot", context);
        assertTrue(snapshot.contains(latest), "最终任务的原文审计不能被选项标签或trim后的摘要替代");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            NONE | 5张改造后加1张改造前，人物53岁，4K。 | AMEND | 6
            layout_v2 | 按这个方案。 | CONFIRM | 6
            count_6 | 删除笑场抓拍，其余保持。 | AMEND | 5
            NONE | 再增加一个俯拍视角，其余保持。 | AMEND | 7
            """)
    void shouldUseSemanticPlanAfterCountClarification(String optionId, String answer,
                                                     String update, int plannedCount) throws Exception {
        Fixture fixture = new Fixture(0, answer);
        fixture.scope.setContextJson("""
                {"askedKeys":["OUTPUT_COUNT"],"pending":{
                  "key":"OUTPUT_COUNT","question":"需要哪些照片？",
                  "imageUpdatePolicy":"KEEP_APPEND",
                  "options":[{"id":"layout_v2","label":"5张改造后加1张改造前"},
                             {"id":"count_6","label":"生成6张"}]}}
                """);
        if (!"NONE".equals(optionId)) fixture.request.setClarificationOptionId(optionId);
        Object context = fixture.resolve(List.of());
        String summary = field(context, "clarificationAnswerSummary");
        assertTrue(summary.contains(answer), "回答必须完整交给模型，不提取局部数字");
        if ("layout_v2".equals(optionId)) assertTrue(summary.contains("5张改造后加1张改造前"));
        ObjectNode args = fixture.mapper.createObjectNode();
        args.put("request_update", update);
        args.put("effective_request", answer);
        args.put("expected_count", plannedCount);
        var jobs = args.putArray("jobs");
        for (int i = 1; i <= plannedCount; i++) {
            ObjectNode job = jobs.addObject();
            job.put("prompt", "人物独立视角照片" + i);
            job.putArray("reference_attachment_ids");
            job.putArray("generated_reference_seqs");
            job.putArray("reference_bindings");
        }
        when(fixture.generationService.submitImageBatch(eq(7L), eq(11L), eq(22L), any(),
                isNull(), isNull(), any())).thenReturn("task-clarified");

        String result = fixture.invoke("doGenerateImages", args, 7L, 11L, 22L, context,
                new ArrayList<>(), privateInstance("ToolExecutionState"));

        assertEquals(plannedCount, fixture.mapper.readTree(result).path("n").asInt(),
                "原文中的分组数量、选项ID或历史数量不能覆盖最新逐张计划");
    }

    @Test
    void shouldNotCarryOldExplicitCountIntoAReplacementWithoutNumericCount() throws Exception {
        Fixture fixture = new Fixture(0, "新任务：分别生成人物正面、侧面、背面照片。");
        Object context = fixture.resolve(List.of());
        ObjectNode args = fixture.mapper.createObjectNode();
        args.put("request_update", "REPLACE");
        args.put("effective_request", fixture.current.getContent());
        args.put("expected_count", 3);
        var jobs = args.putArray("jobs");
        for (int seq = 1; seq <= 3; seq++) {
            ObjectNode job = jobs.addObject();
            job.put("seq", seq);
            job.put("prompt", List.of("人物正面照片", "人物侧面照片", "人物背面照片").get(seq - 1));
            job.putArray("reference_attachment_ids");
            job.putArray("generated_reference_seqs");
            job.putArray("reference_bindings");
        }
        when(fixture.generationService.submitImageBatch(eq(7L), eq(11L), eq(22L), any(),
                isNull(), isNull(), any())).thenReturn("task-replacement");

        String result = fixture.invoke("doGenerateImages", args, 7L, 11L, 22L, context,
                new ArrayList<>(), privateInstance("ToolExecutionState"));

        assertEquals("task-replacement", fixture.mapper.readTree(result).path("task_id").asText(),
                "新完整任务的3个视角不能被原要求中的6张强制补齐");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldUseAmendedEffectiveCountAfterRemovingOneViewWithoutNumericAnswer() throws Exception {
        Fixture fixture = new Fixture(0, "删除笑场抓拍，其余保持。");
        when(fixture.messageService.getById(21L)).thenReturn(message(21L,
                "最终输出6张独立图片：正面、侧面、近景、笑场抓拍、背面、改造前。"));
        Object context = fixture.resolve(List.of());
        String effectiveRequest = "保留正面、侧面、近景、背面和改造前，删除笑场抓拍，其余要求保持。";
        ObjectNode args = fixture.mapper.createObjectNode();
        args.put("request_update", "AMEND");
        args.put("effective_request", effectiveRequest);
        args.put("expected_count", 5);
        var jobs = args.putArray("jobs");
        List<String> prompts = List.of("人物正面照片", "人物侧面照片", "人物近景照片", "人物背面照片", "人物改造前照片");
        for (int seq = 1; seq <= prompts.size(); seq++) {
            ObjectNode job = jobs.addObject();
            job.put("seq", seq);
            job.put("prompt", prompts.get(seq - 1));
            job.putArray("reference_attachment_ids");
            job.putArray("generated_reference_seqs");
            job.putArray("reference_bindings");
        }
        when(fixture.generationService.submitImageBatch(eq(7L), eq(11L), eq(22L), any(),
                isNull(), isNull(), any())).thenReturn("task-amended-five");

        String result = fixture.invoke("doGenerateImages", args, 7L, 11L, 22L, context,
                new ArrayList<>(), privateInstance("ToolExecutionState"));

        JsonNode submitted = fixture.mapper.readTree(result);
        assertEquals("task-amended-five", submitted.path("task_id").asText(),
                "删除一个视角的局部修改即使未写数字，也应采用有效要求中的5张，不能补回原来的6张");
        assertEquals(5, submitted.path("n").asInt());
        ArgumentCaptor<List<GenerateImageItem>> items = ArgumentCaptor.forClass(List.class);
        verify(fixture.generationService).submitImageBatch(eq(7L), eq(11L), eq(22L), items.capture(),
                isNull(), isNull(), any());
        assertEquals(5, items.getValue().size());
        assertEquals(prompts, items.getValue().stream().map(GenerateImageItem::getPlannedPrompt).toList());
        assertTrue(items.getValue().stream().allMatch(item -> effectiveRequest.equals(item.getEffectiveRequest())));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldCompactAmendedCandidatesToReferencedIdsInCandidateOrder() throws Exception {
        Fixture fixture = new Fixture(9, "保留原商品主体，补充本条背景和风格图片。");
        Object context = fixture.resolve(refs("new", 9));
        ObjectNode args = boundSingleJob(fixture.mapper, List.of("new-3", "old-2", "new-1"));
        when(fixture.generationService.submitImageBatch(eq(7L), eq(11L), eq(22L), any(),
                isNull(), isNull(), any())).thenReturn("task-compacted");

        String result = fixture.invoke("doGenerateImages", args, 7L, 11L, 22L, context,
                new ArrayList<>(), privateInstance("ToolExecutionState"));

        assertEquals("task-compacted", fixture.mapper.readTree(result).path("task_id").asText());
        ArgumentCaptor<List<GenerateImageItem>> items = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<String>> urls = ArgumentCaptor.forClass(List.class);
        verify(fixture.generationService).submitImageBatch(eq(7L), eq(11L), eq(22L), items.capture(),
                isNull(), isNull(), urls.capture());
        assertEquals(List.of(url("old", 2), url("new", 1), url("new", 3)), urls.getValue(),
                "整批素材按候选顺序收口，只保留本次实际使用的稳定ID");
        GenerateImageItem item = items.getValue().get(0);
        assertEquals(List.of(3, 1, 2), item.getReferenceImageIndexes(),
                "单张引用顺序必须在收口后映射回相同图片，不能只压缩URL数组");
    }

    @Test
    void shouldReturnToolErrorWhenAmendmentUsesMoreThanNineImages() throws Exception {
        Fixture fixture = new Fixture(9, "补充本条商品背景图片。");
        Object context = fixture.resolve(refs("new", 9));
        List<String> selected = new ArrayList<>(ids("old", 9));
        selected.add("new-1");
        ObjectNode args = boundSingleJob(fixture.mapper, selected);

        Object state = privateInstance("ToolExecutionState");
        assertThrows(ServiceException.class, () -> fixture.invoke("doGenerateImages", args,
                7L, 11L, 22L, context, new ArrayList<>(), state));
        verifyNoInteractions(fixture.generationService);
    }

    private static ObjectNode boundSingleJob(ObjectMapper mapper, List<String> referenceIds) {
        ObjectNode args = mapper.createObjectNode();
        args.put("request_update", "AMEND");
        args.put("effective_request", "生成商品海报，保留指定主体并使用补充背景和风格。");
        args.put("expected_count", 1);
        ObjectNode job = args.putArray("jobs").addObject();
        job.put("seq", 1);
        job.put("prompt", "生成商品海报，清晰展示主体、背景和画面风格。");
        var attachments = job.putArray("reference_attachment_ids");
        referenceIds.forEach(attachments::add);
        job.putArray("generated_reference_seqs");
        var bindings = job.putArray("reference_bindings");
        for (int i = 0; i < referenceIds.size(); i++) {
            ObjectNode binding = bindings.addObject();
            binding.put("source_type", "original");
            binding.put("source_id", referenceIds.get(i));
            binding.putArray("attributes").add("detail_" + i);
            binding.put("usage", "primary");
        }
        return args;
    }

    private record InputRef(String id, String url) {}

    private static List<InputRef> refs(String prefix, int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> new InputRef(prefix + "-" + i, url(prefix, i))).toList();
    }

    private static List<String> ids(String prefix, int count) {
        return refs(prefix, count).stream().map(InputRef::id).toList();
    }

    private static String url(String prefix, int i) {
        return "/profile/agent_reference/7/" + prefix + "-" + i + ".png";
    }

    private static List<String> attachmentIds(Object context) {
        List<?> images = field(context, "images");
        return images.stream().map(image -> AgentReferenceContextTest.<String>field(image, "attachmentId")).toList();
    }

    private static <T> T field(Object target, String accessor) {
        return ReflectionTestUtils.invokeMethod(target, accessor);
    }

    private static Object privateInstance(String nestedType, Object... arguments) throws Exception {
        Class<?> type = Class.forName(AgentService.class.getName() + "$" + nestedType);
        Constructor<?> constructor = type.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        return constructor.newInstance(arguments);
    }

    private static final class Fixture {
        final ObjectMapper mapper = new ObjectMapper();
        final IMessageService messageService = mock(IMessageService.class);
        final IMessageAttachmentService attachmentService = mock(IMessageAttachmentService.class);
        final IReferenceScopeService scopeService = mock(IReferenceScopeService.class);
        final IGenerationTaskService generationService = mock(IGenerationTaskService.class);
        final AgentService service = new AgentService(mock(SysAiModelMapper.class), mock(IConversationService.class),
                messageService, attachmentService, scopeService, generationService, mock(PromptAssembler.class),
                mock(ProfileStorage.class), mock(EncryptUtils.class), mapper, new BillingCalculator(),
                mock(ResponsesApiClient.class), new ApiRateLimiter(50, 300, 3000));
        final ReferenceScope scope = new ReferenceScope();
        final Message current = message(22L, null);
        final AgentChatRequest request = new AgentChatRequest();

        Fixture(int oldCount, String answer) {
            current.setContent(answer);
            Message original = message(21L, ORIGINAL);
            scope.setScopeId("scope-reference");
            scope.setSourceMessageId(21L);
            scope.setRoundNo(1);
            scope.setQuestion("是否按当前要求生成？");
            scope.setContextJson("{\"pending\":{\"key\":\"HARD_CONFLICT\","
                    + "\"question\":\"是否按当前要求生成？\",\"imageUpdatePolicy\":\"KEEP_APPEND\"}}");
            when(scopeService.claim("scope-reference", 11L, 7L, 22L))
                    .thenReturn(new ClaimedReferenceScope(scope, ids("old", oldCount)));
            when(messageService.getById(21L)).thenReturn(original);
            List<MessageAttachment> oldImages = refs("old", oldCount).stream().map(ref -> {
                MessageAttachment image = new MessageAttachment();
                image.setAttachmentId(ref.id());
                image.setProfileUrl(ref.url());
                return image;
            }).toList();
            when(attachmentService.listOwnedOrdered(11L, 7L, ids("old", oldCount))).thenReturn(oldImages);
            request.setConversationId(11L);
            request.setClarificationScopeId("scope-reference");
            request.setMessage(answer);
        }

        Object resolve(List<InputRef> references) throws Exception {
            List<Object> inputs = new ArrayList<>();
            for (int i = 0; i < references.size(); i++) {
                InputRef ref = references.get(i);
                inputs.add(privateInstance("ResolvedInputImage", ref.id(), i + 1, "UPLOAD", ref.url(), null, null));
            }
            return invoke("resolveReferenceContext", 11L, 7L, current, inputs, request);
        }

        Object select(Object context, String update) {
            return invoke("selectPlanReferenceContext", context, update);
        }

        <T> T invoke(String method, Object... arguments) {
            return ReflectionTestUtils.invokeMethod(service, method, arguments);
        }
    }

    private static Message message(Long id, String content) {
        Message message = new Message();
        message.setId(id);
        message.setConversationId(11L);
        message.setUserId(7L);
        message.setRole("user");
        message.setContent(content);
        return message;
    }
}
