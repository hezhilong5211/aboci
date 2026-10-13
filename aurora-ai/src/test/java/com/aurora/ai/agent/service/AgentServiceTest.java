package com.aurora.ai.agent.service;

import com.aurora.ai.agent.billing.BillingCalculator;
import com.aurora.ai.agent.billing.IBeanDeductPort;
import com.aurora.ai.agent.billing.InsufficientBeanException;
import com.aurora.ai.agent.domain.Conversation;
import com.aurora.ai.agent.domain.Message;
import com.aurora.ai.agent.domain.MessageAttachment;
import com.aurora.ai.agent.domain.ReferenceScope;
import com.aurora.ai.agent.dto.ClaimedReferenceScope;
import com.aurora.ai.agent.dto.AgentChatRequest;
import com.aurora.ai.agent.dto.AgentImageInput;
import com.aurora.ai.agent.dto.AgentResumeRequest;
import com.aurora.ai.agent.dto.GenerateImageItem;
import com.aurora.ai.domain.SysAiModel;
import com.aurora.ai.mapper.SysAiModelMapper;
import com.aurora.ai.provider.ResponsesApiClient;
import com.aurora.framework.storage.ProfileStorage;
import com.aurora.framework.mq.ApiRateLimiter;
import com.aurora.framework.utils.EncryptUtils;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import reactor.core.Disposable;
import reactor.core.scheduler.Schedulers;
import com.aurora.ai.agent.domain.ChatRun;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AgentServiceTest {

    @Test
    void writesAtomicActualDebitWhenAnotherConversationHasConsumedPartOfBalance() throws Exception {
        IMessageService messages = mock(IMessageService.class);
        IBeanDeductPort beans = mock(IBeanDeductPort.class);
        AgentService service = new AgentService(mock(SysAiModelMapper.class), mock(IConversationService.class), messages,
                mock(IMessageAttachmentService.class), mock(IReferenceScopeService.class), mock(IGenerationTaskService.class),
                mock(PromptAssembler.class), mock(ProfileStorage.class), mock(EncryptUtils.class), new ObjectMapper(),
                new BillingCalculator(), mock(ResponsesApiClient.class), new ApiRateLimiter(50, 300, 3000));
        ReflectionTestUtils.setField(service, "beanDeductPort", beans);
        Class<?> type = Class.forName(AgentService.class.getName() + "$TokenUsage");
        var constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object usage = constructor.newInstance();
        ReflectionTestUtils.setField(usage, "promptTokens", 2L);
        ReflectionTestUtils.setField(usage, "completionTokens", 1L);
        SysAiModel model = new SysAiModel();
        model.setBeanPerMtokenInput(1_000_000L);
        model.setBeanPerMtokenOutput(1_000_000L);
        Message message = new Message();
        message.setId(22L);
        when(beans.deductUpToBalance(7L, 3L, "msg:22", "AI对话")).thenReturn(2L);
        ReflectionTestUtils.invokeMethod(service, "settleText", 7L, model, usage, message, 21L);
        assertEquals(2, message.getBeanCost());
        assertEquals(3, message.getTokenUsage());
        verify(beans, never()).getBalance(anyLong());
        verify(beans, never()).deduct(anyLong(), anyLong(), anyString(), anyString());
        verify(messages).updateById(message);
    }

    @Test
    void stoppingWorkerBeforeSchedulerStartsItFinishesDurableRunAndReleasesConversation() throws Exception {
        var scheduler = Schedulers.newSingle("test-agent-not-started");
        CountDownLatch blockingWorkerStarted = new CountDownLatch(1);
        CountDownLatch unblockScheduler = new CountDownLatch(1);
        CountDownLatch drained = new CountDownLatch(1);
        scheduler.schedule(() -> {
            blockingWorkerStarted.countDown();
            try { unblockScheduler.await(5, TimeUnit.SECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        try {
            assertTrue(blockingWorkerStarted.await(2, TimeUnit.SECONDS));
            SysAiModelMapper models = mock(SysAiModelMapper.class);
            IConversationService conversations = mock(IConversationService.class);
            IMessageService messages = mock(IMessageService.class);
            ChatRunService runs = mock(ChatRunService.class);
            AgentService service = new AgentService(models, conversations, messages,
                    mock(IMessageAttachmentService.class), mock(IReferenceScopeService.class),
                    mock(IGenerationTaskService.class), mock(PromptAssembler.class), mock(ProfileStorage.class),
                    mock(EncryptUtils.class), new ObjectMapper(), new BillingCalculator(), mock(ResponsesApiClient.class),
                    new ApiRateLimiter(50, 300, 3000));
            ReflectionTestUtils.setField(service, "orchestrationScheduler", scheduler);
            ReflectionTestUtils.setField(service, "chatRunService", runs);
            Conversation conversation = new Conversation();
            conversation.setId(11L);
            when(conversations.getOwned(11L, 7L)).thenReturn(conversation);
            SysAiModel model = new SysAiModel();
            model.setId(9L);
            model.setModelType("chat");
            model.setIsActive(1);
            when(models.selectById(9L)).thenReturn(model);
            ChatRun run = new ChatRun();
            run.setId(1L);
            run.setRequestId("never-started");
            run.setModelId(9L);
            run.setImageModelId(20L);
            run.setStatus("QUEUED");
            when(runs.reserve(eq(7L), eq(11L), eq("never-started"), anyString(), eq(model), eq(20L), isNull()))
                    .thenReturn(new ChatRunService.Reservation(run, true));
            when(runs.applyBillingSnapshot(run, model)).thenReturn(model);
            AgentChatRequest request = new AgentChatRequest();
            request.setRequestId("never-started");
            request.setConversationId(11L);
            request.setModelId(9L);
            request.setImageModelId(20L);
            request.setMessage("你好");
            service.chatStream(7L, request).subscribe();
            assertTrue(service.stopChat(7L, "never-started"));
            assertFalse(service.stopChat(7L, "never-started"));
            verify(runs).finish(run, "CANCELLED");
            unblockScheduler.countDown();
            scheduler.schedule(drained::countDown);
            assertTrue(drained.await(2, TimeUnit.SECONDS));
            verifyNoInteractions(messages);
        } finally {
            unblockScheduler.countDown();
            scheduler.dispose();
        }
    }

    @Test
    void shouldResumePersistedLastUserMessageWithoutSavingDuplicate() {
        SysAiModelMapper modelMapper = mock(SysAiModelMapper.class);
        IConversationService conversationService = mock(IConversationService.class);
        IMessageService messageService = mock(IMessageService.class);
        IMessageAttachmentService attachmentService = mock(IMessageAttachmentService.class);
        IBeanDeductPort beanDeductPort = mock(IBeanDeductPort.class);
        AgentService service = new AgentService(
                modelMapper, conversationService, messageService, attachmentService,
                mock(IReferenceScopeService.class), mock(IGenerationTaskService.class),
                mock(PromptAssembler.class), mock(ProfileStorage.class), mock(EncryptUtils.class),
                new ObjectMapper(), new BillingCalculator(), mock(ResponsesApiClient.class),
                new ApiRateLimiter(50, 300, 3000));
        ReflectionTestUtils.setField(service, "beanDeductPort", beanDeductPort);
        ReflectionTestUtils.setField(service, "minTextBeans", 1L);

        Conversation conversation = new Conversation();
        conversation.setId(11L);
        when(conversationService.getOwned(11L, 7L)).thenReturn(conversation);
        Message source = new Message();
        source.setId(21L);
        source.setConversationId(11L);
        source.setUserId(7L);
        source.setRole("user");
        source.setContent("生成原任务");
        when(messageService.getById(21L)).thenReturn(source);
        when(messageService.listByConversation(11L)).thenReturn(List.of(source));
        when(beanDeductPort.getBalance(7L)).thenReturn(0L);

        AgentResumeRequest request = new AgentResumeRequest();
        request.setConversationId(11L);
        request.setSourceMessageId(21L);
        List<String> events = service.resumeChatStream(7L, request)
                .collectList().block(Duration.ofSeconds(5));

        assertTrue(events != null && events.stream().anyMatch(event ->
                event.contains("\"type\":\"meta\"") && event.contains("\"messageId\":21")));
        assertTrue(events.stream().anyMatch(event -> event.contains("INSUFFICIENT_BEANS")));
        verify(messageService, never()).saveUserMessage(anyLong(), anyLong(), anyString(), any(), any());
        verify(attachmentService).listByMessage(21L);
        verifyNoInteractions(modelMapper);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            1|1|根据4张参考图只做一张商品图，包装标价199元，4K。
            2|2|给两件商品各拍一张正面照。
            3|3|分别生成人物正面、侧面、背面照片。
            4|4|我要四张不同构图的海报。
            5|5|拍5张改造后的照片。
            6|6|生成5张改造后照片，以及1张改造前照片。
            6|6|6张参考图，53岁女性的5张改造后照片和1张改造前照片，共交付6张独立图片。
            6|6|参考图1—6；请拍5张改造后照片，再追加一张改造前对比，不拼图。
            6|6|先生成2张室内，再生成4张室外，附5条标题。
            6|6|Create five after photos and one before photo for a 53-year-old woman.
            7|7|出七张独立图片。
            8|8|需要八张商品照片。
            9|9|给我九张独立海报。
            5|4|请生成5张独立图片。
            """)
    @SuppressWarnings("unchecked")
    void shouldUseSemanticPlanCountWithoutTextNumberOverride(int requested, int planned, String userPrompt)
            throws Exception {
        SysAiModelMapper modelMapper = mock(SysAiModelMapper.class);
        IConversationService conversationService = mock(IConversationService.class);
        IMessageService messageService = mock(IMessageService.class);
        IGenerationTaskService generationTaskService = mock(IGenerationTaskService.class);
        PromptAssembler promptAssembler = mock(PromptAssembler.class);
        EncryptUtils encryptUtils = mock(EncryptUtils.class);
        ResponsesApiClient responsesApiClient = mock(ResponsesApiClient.class);
        IBeanDeductPort beanDeductPort = mock(IBeanDeductPort.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AgentService service = new AgentService(
                modelMapper, conversationService, messageService,
                mock(IMessageAttachmentService.class), mock(IReferenceScopeService.class),
                generationTaskService, promptAssembler,
                new ProfileStorage(Path.of("target/test-agent-count-profile").toString(),
                        Path.of("target/test-agent-count-home").toString()),
                encryptUtils, objectMapper, new BillingCalculator(), responsesApiClient,
                new ApiRateLimiter(50, 300, 3000));
        ReflectionTestUtils.setField(service, "beanDeductPort", beanDeductPort);
        ReflectionTestUtils.setField(service, "minTextBeans", 1L);

        Conversation conversation = new Conversation();
        conversation.setId(11L);
        when(conversationService.createConversation(eq(7L), anyString(), isNull())).thenReturn(conversation);
        Message userMessage = new Message();
        userMessage.setId(21L);
        userMessage.setContent(userPrompt);
        when(messageService.saveUserMessage(eq(11L), eq(7L), anyString(), isNull(), any()))
                .thenReturn(userMessage);
        when(messageService.recentForContext(11L, 11)).thenReturn(List.of(userMessage));

        SysAiModel model = responsesModel();
        when(modelMapper.selectOne(any(Wrapper.class))).thenReturn(model);
        when(encryptUtils.decrypt("encrypted")).thenReturn("key");
        when(beanDeductPort.getBalance(7L)).thenReturn(50L);
        when(promptAssembler.assembleSystemPrompt(eq(7L), isNull(), anyString()))
                .thenReturn(new PromptAssembler.AssembledPrompt("系统提示词", false));
        String plan = imagePlanArguments(objectMapper, requested, planned, userPrompt, false);
        when(responsesApiClient.streamRound(eq(model), eq("key"), any(), any(), any(), any()))
                .thenReturn(toolRound(objectMapper, "call-1", plan),
                        new ResponsesApiClient.Round("已提交", List.of(), objectMapper.createArrayNode(), 2L, 0L, 1L));
        when(generationTaskService.submitImageBatch(eq(7L), eq(11L), eq(21L), any(), isNull(), isNull(), any()))
                .thenReturn("task-1");

        AgentChatRequest request = new AgentChatRequest();
        request.setMessage(userPrompt);
        List<String> events = service.chatStream(7L, request)
                .collectList().block(Duration.ofSeconds(5));

        if (requested != planned) {
            verifyNoInteractions(generationTaskService);
            verify(responsesApiClient, times(1)).streamRound(eq(model), eq("key"), any(), any(), any(), any());
            assertTrue(events.stream().anyMatch(event -> event.contains("生图计划未通过校验")));
            // 即使客户端没有收到 SSE error，历史轮询也能读到终态并解除输入锁定。
            verify(messageService).saveMessage(eq(11L), eq(7L), eq("assistant"),
                    eq("生图计划未通过校验，本次未提交图片，请重试。"), eq("text"),
                    isNull(), isNull(), isNull());
        } else {
            ArgumentCaptor<List<GenerateImageItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
            verify(generationTaskService).submitImageBatch(eq(7L), eq(11L), eq(21L), itemsCaptor.capture(),
                    isNull(), isNull(), any());
            assertEquals(requested, itemsCaptor.getValue().size());
            assertEquals(requested, itemsCaptor.getValue().get(requested - 1).getSeq());
            verify(responsesApiClient, times(2)).streamRound(eq(model), eq("key"), any(), any(), any(), any());
            assertTrue(events.stream().anyMatch(event -> event.contains("\"taskId\":\"task-1\"")));
        }
        assertFalse(events.stream().anyMatch(event -> event.contains("正在校准")));
        ArgumentCaptor<String> runtime = ArgumentCaptor.forClass(String.class);
        verify(promptAssembler).assembleSystemPrompt(eq(7L), isNull(), runtime.capture());
        assertFalse(runtime.getValue().contains("先完成一张正常成品"));
        assertFalse(runtime.getValue().contains("后续直接引用同一基准"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldExecuteSixImageHairWorkflowWhenSkillConflictsAndReferencesAreUnordered() throws Exception {
        SysAiModelMapper modelMapper = mock(SysAiModelMapper.class);
        IConversationService conversationService = mock(IConversationService.class);
        IMessageService messageService = mock(IMessageService.class);
        IReferenceScopeService scopeService = mock(IReferenceScopeService.class);
        IGenerationTaskService generationTaskService = mock(IGenerationTaskService.class);
        PromptAssembler promptAssembler = mock(PromptAssembler.class);
        ProfileStorage profileStorage = mock(ProfileStorage.class);
        EncryptUtils encryptUtils = mock(EncryptUtils.class);
        ResponsesApiClient responsesApiClient = mock(ResponsesApiClient.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AgentService service = new AgentService(
                modelMapper, conversationService, messageService,
                mock(IMessageAttachmentService.class), scopeService,
                generationTaskService, promptAssembler, profileStorage, encryptUtils,
                objectMapper, new BillingCalculator(), responsesApiClient,
                new ApiRateLimiter(50, 300, 3000));

        String prompt = "上传4张乱序参考图：自动识别发型模特、成品环境、服装、改造前环境；"
                + "生成成品主图，再生成正面、侧面、背面、近景，最后生成改造前对比图；"
                + "同时输出小红书标题、文案和话题。";
        Conversation conversation = new Conversation();
        conversation.setId(11L);
        when(conversationService.createConversation(eq(7L), anyString(), isNull())).thenReturn(conversation);
        Message userMessage = new Message();
        userMessage.setId(21L);
        userMessage.setContent(prompt);
        AtomicReference<List<com.aurora.ai.agent.dto.MessageAttachmentDraft>> drafts = new AtomicReference<>();
        when(messageService.saveUserMessage(eq(11L), eq(7L), eq(prompt), anyString(), any()))
                .thenAnswer(invocation -> {
                    drafts.set(invocation.getArgument(4));
                    return userMessage;
                });
        when(messageService.recentForContext(11L, 11)).thenReturn(List.of(userMessage));
        when(profileStorage.read(anyString())).thenReturn(new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47});

        SysAiModel model = responsesModel();
        when(modelMapper.selectOne(any(Wrapper.class))).thenReturn(model);
        when(encryptUtils.decrypt("encrypted")).thenReturn("key");
        when(promptAssembler.assembleSystemPrompt(eq(7L), eq("冲突Skill"), anyString()))
                .thenReturn(new PromptAssembler.AssembledPrompt("用户要求高于Skill", true));
        when(responsesApiClient.streamRound(eq(model), eq("key"), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    List<String> attachmentIds = drafts.get().stream()
                            .map(com.aurora.ai.agent.dto.MessageAttachmentDraft::attachmentId)
                            .toList();
                    var args = objectMapper.createObjectNode();
                    args.put("expected_count", 6);
                    args.put("request_update", "REPLACE");
                    args.put("effective_request", prompt);
                    var jobs = args.putArray("jobs");
                    for (int seq = 1; seq <= 6; seq++) {
                        var job = jobs.addObject();
                        job.put("seq", seq);
                        job.put("prompt", switch (seq) {
                            case 1 -> "成品主图";
                            case 2 -> "正面发型图";
                            case 3 -> "侧面发型图";
                            case 4 -> "背面发型图";
                            case 5 -> "近景发型图";
                            default -> "改造前对比图";
                        });
                        var refs = job.putArray("reference_attachment_ids");
                        var bindings = job.putArray("reference_bindings");
                        if (seq == 6) {
                            refs.add(attachmentIds.get(2)).add(attachmentIds.get(3));
                            addBinding(bindings, "original", attachmentIds.get(2), "primary", "scene");
                        } else {
                            refs.add(attachmentIds.get(1)).add(attachmentIds.get(0)).add(attachmentIds.get(3));
                            addBinding(bindings, "original", attachmentIds.get(1), "primary", "hair_structure");
                            addBinding(bindings, "original", attachmentIds.get(0), "primary", "scene");
                        }
                        addBinding(bindings, "original", attachmentIds.get(3), "primary", "outfit");
                        var generated = job.putArray("generated_reference_seqs");
                    }
                    return new ResponsesApiClient.Round(
                            "", List.of(new ResponsesApiClient.ToolCall(
                            "call-six", "generate_images", args.toString())),
                            objectMapper.createArrayNode(), 10L, 0L, 2L);
                })
                .thenReturn(new ResponsesApiClient.Round(
                        "已提交", List.of(), objectMapper.createArrayNode(), 2L, 0L, 1L))
                .thenReturn(new ResponsesApiClient.Round(
                        "```aurora-result\n{\"title\":\"白发改造\",\"body\":\"文案\","
                                + "\"tags\":[\"白发和解\"]}\n```",
                        List.of(), objectMapper.createArrayNode(), 1L, 0L, 1L));
        when(generationTaskService.submitImageBatch(eq(7L), eq(11L), eq(21L), any(), isNull(), isNull(), any()))
                .thenReturn("task-six");
        Message assistant = new Message();
        assistant.setId(22L);
        when(messageService.saveMessage(eq(11L), eq(7L), eq("assistant"), anyString(),
                eq("structured"), anyString(), isNull(), isNull())).thenReturn(assistant);

        AgentChatRequest request = new AgentChatRequest();
        request.setMessage(prompt);
        request.setSkill("冲突Skill");
        request.setImages(List.of(
                "/profile/agent_reference/7/scene.png",
                "/profile/agent_reference/7/hair.png",
                "/profile/agent_reference/7/before.png",
                "/profile/agent_reference/7/clothes.png"));
        List<String> events = service.chatStream(7L, request)
                .collectList().block(Duration.ofSeconds(5));

        ArgumentCaptor<List<GenerateImageItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<String>> refsCaptor = ArgumentCaptor.forClass(List.class);
        verify(generationTaskService).submitImageBatch(eq(7L), eq(11L), eq(21L), itemsCaptor.capture(),
                isNull(), isNull(), refsCaptor.capture());
        assertEquals(6, itemsCaptor.getValue().size());
        assertEquals(4, refsCaptor.getValue().size());
        assertTrue(itemsCaptor.getValue().get(1).getPrompt()
                .contains("本任务输入图1（上传素材）：主要依据：hair_structure。"));
        assertEquals(List.of(2, 1, 4), itemsCaptor.getValue().get(1).getReferenceImageIndexes());
        assertTrue(itemsCaptor.getValue().get(1).getPrompt()
                .contains("本任务输入图1（上传素材）：主要依据：hair_structure。"));
        GenerateImageItem before = itemsCaptor.getValue().get(5);
        assertEquals(List.of(3, 4), before.getReferenceImageIndexes());
        assertTrue(before.getPrompt().contains("本任务输入图1（上传素材）：主要依据：scene。"));
        assertFalse(before.getPrompt().contains("hair_structure"));
        assertTrue(itemsCaptor.getValue().stream().allMatch(item ->
                item.getContractVersion() == 5
                        && item.getSourcePrompt().contains(prompt)
                        && !item.getPrompt().contains(prompt)));
        assertTrue(events != null && events.stream().anyMatch(event ->
                event.contains("\"taskId\":\"task-six\"") && event.contains("\"n\":6")));
        assertFalse(events.stream().anyMatch(event ->
                event.contains("\"type\":\"clarification\"") || event.contains("\"type\":\"error\"")));
        verify(scopeService).cancelOpen(11L, 7L);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 3})
    @SuppressWarnings("unchecked")
    void shouldCompileProductReferencesAndRepairAmbiguousNumberingBeforeSubmitting(int invalidRounds) throws Exception {
        SysAiModelMapper modelMapper = mock(SysAiModelMapper.class);
        IConversationService conversationService = mock(IConversationService.class);
        IMessageService messageService = mock(IMessageService.class);
        IGenerationTaskService generationTaskService = mock(IGenerationTaskService.class);
        PromptAssembler promptAssembler = mock(PromptAssembler.class);
        ProfileStorage profileStorage = mock(ProfileStorage.class);
        EncryptUtils encryptUtils = mock(EncryptUtils.class);
        ResponsesApiClient responsesApiClient = mock(ResponsesApiClient.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AgentService service = new AgentService(
                modelMapper, conversationService, messageService,
                mock(IMessageAttachmentService.class), mock(IReferenceScopeService.class),
                generationTaskService, promptAssembler, profileStorage, encryptUtils,
                objectMapper, new BillingCalculator(), responsesApiClient,
                new ApiRateLimiter(50, 300, 3000));

        String prompt = "生成2张独立商品照片。图1只提供木质桌面的场景，图2只提供保温杯形状。"
                + "先生成完整商品主图；再沿用生成主图的杯体形状拍摄包装标签近景，"
                + "标签内容只使用图3，环境继续使用图1。";
        Conversation conversation = new Conversation();
        conversation.setId(11L);
        when(conversationService.createConversation(eq(7L), anyString(), isNull())).thenReturn(conversation);
        Message userMessage = new Message();
        userMessage.setId(21L);
        userMessage.setContent(prompt);
        AtomicReference<List<com.aurora.ai.agent.dto.MessageAttachmentDraft>> drafts = new AtomicReference<>();
        when(messageService.saveUserMessage(eq(11L), eq(7L), eq(prompt), anyString(), any()))
                .thenAnswer(invocation -> {
                    drafts.set(invocation.getArgument(4));
                    return userMessage;
                });
        when(messageService.recentForContext(11L, 11)).thenReturn(List.of(userMessage));
        when(profileStorage.read(anyString())).thenReturn(new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47});
        SysAiModel model = responsesModel();
        when(modelMapper.selectOne(any(Wrapper.class))).thenReturn(model);
        when(encryptUtils.decrypt("encrypted")).thenReturn("key");
        when(promptAssembler.assembleSystemPrompt(eq(7L), isNull(), anyString()))
                .thenReturn(new PromptAssembler.AssembledPrompt("系统提示词", false));
        AtomicInteger rounds = new AtomicInteger();
        when(responsesApiClient.streamRound(eq(model), eq("key"), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    int round = rounds.incrementAndGet();
                    if (round > (invalidRounds + 1)) {
                        return new ResponsesApiClient.Round(
                                "已提交", List.of(), objectMapper.createArrayNode(), 2L, 0L, 1L);
                    }
                    verifyNoInteractions(generationTaskService);
                    List<String> ids = drafts.get().stream()
                            .map(com.aurora.ai.agent.dto.MessageAttachmentDraft::attachmentId).toList();
                    var args = objectMapper.createObjectNode();
                    args.put("request_update", "REPLACE");
                    args.put("effective_request", prompt);
                    args.put("expected_count", 2);
                    var jobs = args.putArray("jobs");
                    var hero = jobs.addObject();
                    hero.put("seq", 1);
                    hero.put("prompt", round <= invalidRounds ? "参考图2提供保温杯，参考图1提供桌面。"
                            : "[[ref:" + ids.get(1) + "]]提供保温杯，保留轮廓与比例；[[ref:" + ids.get(0) + "]]提供木质桌面，商业静物摄影。");
                    hero.putArray("reference_attachment_ids").add(ids.get(1)).add(ids.get(0));
                    hero.putArray("generated_reference_seqs");
                    var heroBindings = hero.putArray("reference_bindings");
                    addBinding(heroBindings, "original", ids.get(1), "primary", "product_geometry");
                    addBinding(heroBindings, "original", ids.get(0), "primary", "scene");
                    var detail = jobs.addObject();
                    detail.put("seq", 2);
                    detail.put("prompt", "保温杯包装标签近景，杯体形状与比例保持一致，准确呈现标签内容，背景为木质桌面。");
                    detail.putArray("reference_attachment_ids").add(ids.get(2)).add(ids.get(0)).add(ids.get(1));
                    detail.putArray("generated_reference_seqs");
                    var detailBindings = detail.putArray("reference_bindings");
                    addBinding(detailBindings, "original", ids.get(1), "primary", "product_geometry");
                    addBinding(detailBindings, "original", ids.get(2), "primary", "product_label");
                    addBinding(detailBindings, "original", ids.get(0), "primary",
                            "scene");
                    return toolRound(objectMapper, "call-product-" + round, args.toString());
                });
        when(generationTaskService.submitImageBatch(eq(7L), eq(11L), eq(21L), any(), isNull(), isNull(), any()))
                .thenReturn("task-product");

        AgentChatRequest request = new AgentChatRequest();
        request.setMessage(prompt);
        request.setImages(List.of("/profile/agent_reference/7/scene.png",
                "/profile/agent_reference/7/product.png", "/profile/agent_reference/7/label.png"));
        List<String> events = service.chatStream(7L, request).collectList().block(Duration.ofSeconds(5));
        if (invalidRounds > 2) {
            verifyNoInteractions(generationTaskService);
            assertEquals(3, rounds.get());
            assertTrue(events.stream().anyMatch(event -> event.contains("连续校验失败")));
            return;
        }
        if (invalidRounds > 0) assertTrue(events.stream().anyMatch(event -> event.contains("正在校对图片引用")));

        ArgumentCaptor<List<GenerateImageItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<String>> refsCaptor = ArgumentCaptor.forClass(List.class);
        verify(generationTaskService).submitImageBatch(eq(7L), eq(11L), eq(21L), itemsCaptor.capture(),
                isNull(), isNull(), refsCaptor.capture());
        assertEquals(request.getImages(), refsCaptor.getValue());
        assertEquals(2, itemsCaptor.getValue().size());
        GenerateImageItem hero = itemsCaptor.getValue().get(0);
        assertEquals(List.of(2, 1), hero.getReferenceImageIndexes());
        assertTrue(hero.getPrompt().contains("本任务输入图1（上传素材）：主要依据：product_geometry。"));
        assertTrue(hero.getPrompt().contains("本任务输入图2（上传素材）：主要依据：scene。"));
        GenerateImageItem detail = itemsCaptor.getValue().get(1);
        assertEquals(List.of(3, 1, 2), detail.getReferenceImageIndexes());
        assertTrue(detail.getPrompt().contains("本任务输入图3（上传素材）：主要依据：product_geometry。"));
        assertTrue(detail.getPrompt().contains("本任务输入图1（上传素材）：主要依据：product_label。"));
        assertTrue(detail.getPrompt().contains("本任务输入图2（上传素材）：主要依据：scene。"));
        assertTrue(itemsCaptor.getValue().stream().allMatch(item ->
                item.getContractVersion() == 5
                        && item.getReferenceAttachmentIds().equals(drafts.get().stream()
                                .map(com.aurora.ai.agent.dto.MessageAttachmentDraft::attachmentId).toList())
                        && item.getSourcePrompt().contains(prompt)
                        && !item.getPrompt().contains(prompt)
                        && !item.getPrompt().contains("identity")
                        && !item.getPrompt().contains("整体造型一致性锚点")));
        assertEquals(invalidRounds + 2, rounds.get());
        assertTrue(hero.getPrompt().contains("本任务输入图1提供保温杯"));
        assertFalse(hero.getPrompt().contains("[[ref:"));
        assertTrue(events != null && events.stream().anyMatch(event -> event.contains("\"taskId\":\"task-product\"")));
        assertFalse(events.stream().anyMatch(event -> event.contains("\"status\":\"failed\"")));
    }

    @Test
    void shouldAskForCountWhenModelFindsGenuineAmbiguity() throws Exception {
        SysAiModelMapper modelMapper = mock(SysAiModelMapper.class);
        IConversationService conversationService = mock(IConversationService.class);
        IMessageService messageService = mock(IMessageService.class);
        IReferenceScopeService scopeService = mock(IReferenceScopeService.class);
        IGenerationTaskService generationTaskService = mock(IGenerationTaskService.class);
        PromptAssembler promptAssembler = mock(PromptAssembler.class);
        EncryptUtils encryptUtils = mock(EncryptUtils.class);
        ResponsesApiClient responsesApiClient = mock(ResponsesApiClient.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AgentService service = new AgentService(
                modelMapper, conversationService, messageService,
                mock(IMessageAttachmentService.class), scopeService,
                generationTaskService, promptAssembler,
                new ProfileStorage(Path.of("target/test-agent-clarify-count-profile").toString(),
                        Path.of("target/test-agent-clarify-count-home").toString()),
                encryptUtils, objectMapper, new BillingCalculator(), responsesApiClient,
                new ApiRateLimiter(50, 300, 3000));

        Conversation conversation = new Conversation();
        conversation.setId(11L);
        when(conversationService.createConversation(eq(7L), anyString(), isNull())).thenReturn(conversation);
        Message userMessage = new Message();
        userMessage.setId(21L);
        userMessage.setContent("按这些照片做一套内容");
        when(messageService.saveUserMessage(eq(11L), eq(7L), anyString(), isNull(), any()))
                .thenReturn(userMessage);
        when(messageService.recentForContext(11L, 11)).thenReturn(List.of(userMessage));

        SysAiModel model = responsesModel();
        when(modelMapper.selectOne(any(Wrapper.class))).thenReturn(model);
        when(encryptUtils.decrypt("encrypted")).thenReturn("key");
        when(promptAssembler.assembleSystemPrompt(eq(7L), isNull(), anyString()))
                .thenReturn(new PromptAssembler.AssembledPrompt("系统提示词", false));
        String clarificationArgs = """
                {"key":"OUTPUT_COUNT","question":"我还不能确定这次需要生成几张图片，请告诉我 1～9 张中的具体数量。",
                 "image_update_policy":"KEEP_APPEND","allow_free_text":true}
                """;
        when(responsesApiClient.streamRound(eq(model), eq("key"), any(), any(), any(), any()))
                .thenReturn(new ResponsesApiClient.Round("", List.of(new ResponsesApiClient.ToolCall(
                        "call-count", "request_clarification", clarificationArgs)),
                        objectMapper.createArrayNode(), 2L, 0L, 1L));
        ReferenceScope opened = new ReferenceScope();
        opened.setScopeId("scope-count");
        when(scopeService.open(eq(11L), eq(7L), eq(21L), eq(List.of()), anyString(), eq(true),
                anyString(), anyInt(), any())).thenReturn(opened);
        Message assistant = new Message();
        assistant.setId(22L);
        when(messageService.saveMessage(eq(11L), eq(7L), eq("assistant"), anyString(),
                eq("clarification"), anyString(), isNull(), isNull())).thenReturn(assistant);

        AgentChatRequest request = new AgentChatRequest();
        request.setMessage("按这些照片做一套内容");
        List<String> events = service.chatStream(7L, request)
                .collectList().block(Duration.ofSeconds(5));

        verifyNoInteractions(generationTaskService);
        verify(messageService).saveMessage(eq(11L), eq(7L), eq("assistant"),
                eq("我还不能确定这次需要生成几张图片，请告诉我 1～9 张中的具体数量。"),
                eq("clarification"), anyString(), isNull(), isNull());
        assertTrue(events != null && events.stream().anyMatch(event ->
                event.contains("\"type\":\"clarification\"")
                        && event.contains("我还不能确定这次需要生成几张图片")));
        assertFalse(events.stream().anyMatch(event ->
                event.contains("\"status\":\"failed\"") || event.contains("批量任务项数量")));
    }

    @Test
    void shouldOfferAffordableCountAndPersistParsedPlanWhenImageBalanceIsInsufficient() throws Exception {
        SysAiModelMapper modelMapper = mock(SysAiModelMapper.class);
        IConversationService conversationService = mock(IConversationService.class);
        IMessageService messageService = mock(IMessageService.class);
        IReferenceScopeService scopeService = mock(IReferenceScopeService.class);
        IGenerationTaskService generationTaskService = mock(IGenerationTaskService.class);
        PromptAssembler promptAssembler = mock(PromptAssembler.class);
        EncryptUtils encryptUtils = mock(EncryptUtils.class);
        ResponsesApiClient responsesApiClient = mock(ResponsesApiClient.class);
        IBeanDeductPort beanDeductPort = mock(IBeanDeductPort.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AgentService service = new AgentService(
                modelMapper, conversationService, messageService,
                mock(IMessageAttachmentService.class), scopeService,
                generationTaskService, promptAssembler,
                mock(ProfileStorage.class), encryptUtils, objectMapper,
                new BillingCalculator(), responsesApiClient,
                new ApiRateLimiter(50, 300, 3000));
        ReflectionTestUtils.setField(service, "beanDeductPort", beanDeductPort);
        ReflectionTestUtils.setField(service, "minTextBeans", 1L);

        Conversation conversation = new Conversation();
        conversation.setId(11L);
        when(conversationService.createConversation(eq(7L), anyString(), isNull())).thenReturn(conversation);
        Message userMessage = new Message();
        userMessage.setId(21L);
        userMessage.setContent("生成5张图");
        when(messageService.saveUserMessage(eq(11L), eq(7L), eq("生成5张图"), isNull(), any()))
                .thenReturn(userMessage);
        when(messageService.recentForContext(11L, 11)).thenReturn(List.of(userMessage));
        when(beanDeductPort.getBalance(7L)).thenReturn(10L);

        SysAiModel model = responsesModel();
        when(modelMapper.selectOne(any(Wrapper.class))).thenReturn(model);
        when(encryptUtils.decrypt("encrypted")).thenReturn("key");
        when(promptAssembler.assembleSystemPrompt(eq(7L), isNull(), anyString()))
                .thenReturn(new PromptAssembler.AssembledPrompt("系统提示词", false));
        when(responsesApiClient.streamRound(eq(model), eq("key"), any(), any(), any(), any()))
                .thenReturn(toolRound(objectMapper, "call-balance",
                        imagePlanArguments(objectMapper, 5, 5)));
        when(generationTaskService.submitImageBatch(
                eq(7L), eq(11L), eq(21L), any(), isNull(), isNull(), eq(List.of())))
                .thenThrow(new InsufficientBeanException(10L, 30L, 6L));
        ReferenceScope opened = new ReferenceScope();
        opened.setScopeId("scope-balance");
        ArgumentCaptor<String> contextCaptor = ArgumentCaptor.forClass(String.class);
        when(scopeService.open(eq(11L), eq(7L), eq(21L), eq(List.of()), anyString(), eq(false),
                contextCaptor.capture(), anyInt(), any())).thenReturn(opened);
        Message assistant = new Message();
        assistant.setId(22L);
        when(messageService.saveMessage(eq(11L), eq(7L), eq("assistant"), anyString(),
                eq("clarification"), anyString(), isNull(), isNull())).thenReturn(assistant);

        AgentChatRequest request = new AgentChatRequest();
        request.setMessage("生成5张图");
        List<String> events = service.chatStream(7L, request)
                .collectList().block(Duration.ofSeconds(5));

        JsonNode context = objectMapper.readTree(contextCaptor.getValue());
        assertEquals(5, context.path("affordablePlan").path("requestedCount").asInt());
        assertEquals(1, context.path("affordablePlan").path("affordableCount").asInt());
        assertEquals(5, context.path("affordablePlan").path("items").size());
        JsonNode savedItem = context.path("affordablePlan").path("items").get(0);
        assertEquals(5, savedItem.path("contractVersion").asInt());
        assertEquals("生成5张图", savedItem.path("sourcePrompt").asText());
        assertEquals("生成5张图", savedItem.path("effectiveRequest").asText());
        assertEquals("REPLACE", savedItem.path("requestUpdate").asText());
        assertTrue(savedItem.path("referenceBindings").isArray());
        assertTrue(savedItem.path("prompt").asText().contains("独立摄影构图方案A"));
        assertTrue(events != null && events.stream().anyMatch(event ->
                event.contains("\"type\":\"clarification\"")
                        && event.contains("\"key\":\"INSUFFICIENT_BALANCE\"")
                        && event.contains("当前余额 10 个豆子")
                        && event.contains("本次 5 张共需 30 个")
                        && event.contains("生成 1 张")
                        && event.contains("去充值")));
        assertFalse(events.stream().anyMatch(event ->
                event.contains("\"type\":\"error\"") || event.contains("\"status\":\"failed\"")));
        verify(beanDeductPort, never()).deductUpToBalance(anyLong(), anyLong(), anyString(), anyString());
        verify(messageService).updateById(assistant);
        assertEquals(0, assistant.getBeanCost());
        assertEquals(2, assistant.getTokenUsage());
    }

    @Test
    void shouldSubmitSavedPlanWithoutCallingChatModelAgain() throws Exception {
        assertSavedPlanSubmission(
                "generate_affordable", "生成 1 张", 1, "task-affordable");
        assertSavedPlanSubmission(
                "generate_requested", "继续生成", 5, "task-requested");
    }

    @Test
    void shouldRejectSavedDependentPlanBeforeAnyChargeEvenWhenOnlyFirstItemIsAffordable() throws Exception {
        assertSavedPlanSubmission("generate_affordable", "生成 1 张", 1, "must-not-submit", true);
    }

    private void assertSavedPlanSubmission(String optionId, String answerText,
                                           int expectedCount, String taskId) throws Exception {
        assertSavedPlanSubmission(optionId, answerText, expectedCount, taskId, false);
    }

    @SuppressWarnings("unchecked")
    private void assertSavedPlanSubmission(String optionId, String answerText,
                                           int expectedCount, String taskId, boolean legacyDependent) throws Exception {
        SysAiModelMapper modelMapper = mock(SysAiModelMapper.class);
        IConversationService conversationService = mock(IConversationService.class);
        IMessageService messageService = mock(IMessageService.class);
        IMessageAttachmentService attachmentService = mock(IMessageAttachmentService.class);
        IReferenceScopeService scopeService = mock(IReferenceScopeService.class);
        IGenerationTaskService generationTaskService = mock(IGenerationTaskService.class);
        PromptAssembler promptAssembler = mock(PromptAssembler.class);
        ResponsesApiClient responsesApiClient = mock(ResponsesApiClient.class);
        IBeanDeductPort beanDeductPort = mock(IBeanDeductPort.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AgentService service = new AgentService(
                modelMapper, conversationService, messageService, attachmentService,
                scopeService, generationTaskService, promptAssembler,
                mock(ProfileStorage.class), mock(EncryptUtils.class), objectMapper,
                new BillingCalculator(), responsesApiClient,
                new ApiRateLimiter(50, 300, 3000));
        ReflectionTestUtils.setField(service, "beanDeductPort", beanDeductPort);
        ReflectionTestUtils.setField(service, "minTextBeans", 1L);

        Conversation conversation = new Conversation();
        conversation.setId(11L);
        when(conversationService.getOwned(11L, 7L)).thenReturn(conversation);
        Message answer = new Message();
        answer.setId(22L);
        answer.setContent(answerText);
        when(messageService.saveUserMessage(eq(11L), eq(7L), eq(answerText), isNull(), any()))
                .thenReturn(answer);
        when(beanDeductPort.getBalance(7L)).thenReturn(10L);

        Message source = new Message();
        source.setId(21L);
        source.setConversationId(11L);
        source.setUserId(7L);
        source.setRole("user");
        source.setContent("生成5张图");
        when(messageService.getById(21L)).thenReturn(source);
        ReferenceScope scope = new ReferenceScope();
        scope.setScopeId("scope-balance");
        scope.setSourceMessageId(21L);
        scope.setRoundNo(1);
        scope.setQuestion("余额不足");
        scope.setContextJson(affordablePlanContext(objectMapper, 5, 1));
        if (legacyDependent) {
            var oldContext = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(scope.getContextJson());
            ((com.fasterxml.jackson.databind.node.ObjectNode) oldContext.path("affordablePlan").path("items").get(1))
                    .putArray("generatedReferenceSeqs").add(1);
            scope.setContextJson(oldContext.toString());
        }
        when(scopeService.claim("scope-balance", 11L, 7L, 22L))
                .thenReturn(new ClaimedReferenceScope(scope, List.of()));
        when(attachmentService.listOwnedOrdered(11L, 7L, List.of())).thenReturn(List.of());
        when(generationTaskService.submitImageBatch(
                eq(7L), eq(11L), eq(22L), any(), isNull(), isNull(), eq(List.of())))
                .thenReturn(taskId);
        Message assistant = new Message();
        assistant.setId(23L);
        when(messageService.saveMessage(eq(11L), eq(7L), eq("assistant"), anyString(),
                eq("structured"), anyString(), isNull(), isNull())).thenReturn(assistant);

        AgentChatRequest request = new AgentChatRequest();
        request.setConversationId(11L);
        request.setMessage(answerText);
        request.setClarificationScopeId("scope-balance");
        request.setClarificationOptionId(optionId);
        List<String> events = service.chatStream(7L, request)
                .collectList().block(Duration.ofSeconds(5));

        if (legacyDependent) {
            verifyNoInteractions(generationTaskService, modelMapper, responsesApiClient);
            verify(beanDeductPort, never()).deductUpToBalance(anyLong(), anyLong(), anyString(), anyString());
            assertTrue(events.stream().anyMatch(event -> event.contains("不支持任务内生成图引用")));
            return;
        }
        ArgumentCaptor<List<GenerateImageItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(generationTaskService).submitImageBatch(
                eq(7L), eq(11L), eq(22L), itemsCaptor.capture(), isNull(), isNull(), eq(List.of()));
        assertEquals(expectedCount, itemsCaptor.getValue().size());
        assertEquals("第 1 张图", itemsCaptor.getValue().get(0).getPrompt());
        assertEquals("第 " + expectedCount + " 张图",
                itemsCaptor.getValue().get(expectedCount - 1).getPrompt());
        assertTrue(events != null && events.stream().anyMatch(event ->
                event.contains("\"taskId\":\"" + taskId + "\"")
                        && event.contains("\"n\":" + expectedCount)));
        assertFalse(events.stream().anyMatch(event -> event.contains("\"type\":\"error\"")));
        verifyNoInteractions(modelMapper, promptAssembler, responsesApiClient);
        verify(beanDeductPort, never()).deductUpToBalance(anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    void shouldRestorePendingClarificationForConversationHistory() {
        IReferenceScopeService scopeService = mock(IReferenceScopeService.class);
        AgentService service = new AgentService(
                mock(SysAiModelMapper.class), mock(IConversationService.class), mock(IMessageService.class),
                mock(IMessageAttachmentService.class), scopeService, mock(IGenerationTaskService.class),
                mock(PromptAssembler.class), mock(ProfileStorage.class), mock(EncryptUtils.class),
                new ObjectMapper(), new BillingCalculator(), mock(ResponsesApiClient.class),
                new ApiRateLimiter(50, 300, 3000));
        ReferenceScope scope = new ReferenceScope();
        scope.setScopeId("scope-history");
        scope.setRoundNo(2);
        scope.setContextJson("{\"pending\":{\"key\":\"REFERENCE_MAPPING\","
                + "\"question\":\"哪张是服装参考图？\",\"allowFreeText\":true,"
                + "\"imageUpdatePolicy\":\"KEEP_APPEND\"}}");
        when(scopeService.getOpen(11L, 7L)).thenReturn(scope);

        var clarification = service.getPendingClarification(7L, 11L);

        assertEquals("scope-history", clarification.getScopeId());
        assertEquals("REFERENCE_MAPPING", clarification.getKey());
        assertEquals(2, clarification.getRound());
        assertEquals("哪张是服装参考图？", clarification.getQuestion());
    }

    @Test
    void shouldRemoveDuplicatedTagSectionFromStructuredTitle() {
        String title = "封面标题：\n1. 白发和解\n\n笔记标题：\n1. 自然花白\n\n话题标签：\n#白发和解";

        assertEquals("封面标题：\n1. 白发和解\n\n笔记标题：\n1. 自然花白",
                AgentService.normalizeStructuredTitle(title));
    }

    @Test
    void shouldKeepNormalStructuredTitle() {
        assertEquals("封面标题：\n1. 白发和解",
                AgentService.normalizeStructuredTitle("封面标题：\n1. 白发和解"));
    }

    @Test
    void shouldNotDeductTwiceWhenAssistantUsageUpdateFails() throws Exception {
        SysAiModelMapper modelMapper = mock(SysAiModelMapper.class);
        IConversationService conversationService = mock(IConversationService.class);
        IMessageService messageService = mock(IMessageService.class);
        IGenerationTaskService generationTaskService = mock(IGenerationTaskService.class);
        PromptAssembler promptAssembler = mock(PromptAssembler.class);
        EncryptUtils encryptUtils = mock(EncryptUtils.class);
        ResponsesApiClient responsesApiClient = mock(ResponsesApiClient.class);
        IBeanDeductPort beanDeductPort = mock(IBeanDeductPort.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AgentService service = new AgentService(
                modelMapper, conversationService, messageService,
                mock(IMessageAttachmentService.class), mock(IReferenceScopeService.class),
                generationTaskService, promptAssembler,
                new ProfileStorage(Path.of("target/test-agent-profile").toString(),
                        Path.of("target/test-agent-home").toString()),
                encryptUtils, objectMapper, new BillingCalculator(), responsesApiClient,
                new ApiRateLimiter(50, 300, 3000));
        ReflectionTestUtils.setField(service, "beanDeductPort", beanDeductPort);
        ReflectionTestUtils.setField(service, "minTextBeans", 1L);

        Conversation conversation = new Conversation();
        conversation.setId(11L);
        when(conversationService.createConversation(eq(7L), anyString(), isNull())).thenReturn(conversation);

        Message userMessage = new Message();
        userMessage.setId(21L);
        Message assistantMessage = new Message();
        assistantMessage.setId(22L);
        when(messageService.saveUserMessage(eq(11L), eq(7L), anyString(), isNull(), any()))
                .thenReturn(userMessage);
        when(messageService.saveMessage(eq(11L), eq(7L), eq("assistant"), eq("完成"), eq("text"),
                isNull(), isNull(), isNull())).thenReturn(assistantMessage);
        when(messageService.recentForContext(11L, 11)).thenReturn(List.of(userMessage));

        SysAiModel model = new SysAiModel();
        model.setId(9L);
        model.setModelType("chat");
        model.setIsActive(1);
        model.setApiProtocol("responses");
        model.setApiKey("encrypted");
        model.setBeanPerMtokenInput(1_000_000L);
        model.setBeanPerMtokenOutput(1_000_000L);
        when(modelMapper.selectOne(any(Wrapper.class))).thenReturn(model);
        when(encryptUtils.decrypt("encrypted")).thenReturn("key");
        when(promptAssembler.assembleSystemPrompt(eq(7L), isNull(), anyString()))
                .thenReturn(new PromptAssembler.AssembledPrompt("系统提示词", false));
        when(responsesApiClient.streamRound(eq(model), eq("key"), any(), any(), any(), any()))
                .thenReturn(new ResponsesApiClient.Round(
                        "完成", List.of(), objectMapper.createArrayNode(), 2L, 1L, 0L));
        when(beanDeductPort.getBalance(7L)).thenReturn(100L);
        doThrow(new RuntimeException("消息用量回写失败")).when(messageService).updateById(assistantMessage);
        when(beanDeductPort.deductUpToBalance(7L, 3L, "msg:22", "AI对话")).thenReturn(3L);

        AgentChatRequest request = new AgentChatRequest();
        request.setMessage("你好");
        List<String> events = service.chatStream(7L, request)
                .collectList().block(Duration.ofSeconds(5));

        assertTrue(events != null && !events.isEmpty());
        verify(beanDeductPort, timeout(3000)).deductUpToBalance(7L, 3L, "msg:22", "AI对话");
        verify(beanDeductPort, never()).deductUpToBalance(eq(7L), anyLong(), eq("msg:21"), anyString());
        verify(messageService, never()).saveMessage(eq(11L), eq(7L), eq("assistant"), eq("对话已停止"),
                anyString(), any(), any(), any());
    }

    @Test
    void shouldStopLaterModelRoundsButKeepSubmittedImageTask() throws Exception {
        SysAiModelMapper modelMapper = mock(SysAiModelMapper.class);
        IConversationService conversationService = mock(IConversationService.class);
        IMessageService messageService = mock(IMessageService.class);
        IGenerationTaskService generationTaskService = mock(IGenerationTaskService.class);
        PromptAssembler promptAssembler = mock(PromptAssembler.class);
        EncryptUtils encryptUtils = mock(EncryptUtils.class);
        ResponsesApiClient responsesApiClient = mock(ResponsesApiClient.class);
        IBeanDeductPort beanDeductPort = mock(IBeanDeductPort.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AgentService service = new AgentService(
                modelMapper, conversationService, messageService,
                mock(IMessageAttachmentService.class), mock(IReferenceScopeService.class),
                generationTaskService, promptAssembler,
                new ProfileStorage(Path.of("target/test-agent-cancel-profile").toString(),
                        Path.of("target/test-agent-cancel-home").toString()),
                encryptUtils, objectMapper, new BillingCalculator(), responsesApiClient,
                new ApiRateLimiter(50, 300, 3000));
        ReflectionTestUtils.setField(service, "beanDeductPort", beanDeductPort);
        ReflectionTestUtils.setField(service, "minTextBeans", 1L);

        Conversation conversation = new Conversation();
        conversation.setId(11L);
        when(conversationService.createConversation(eq(7L), anyString(), isNull())).thenReturn(conversation);

        Message userMessage = new Message();
        userMessage.setId(21L);
        userMessage.setContent("生成1张图");
        Message stoppedMessage = new Message();
        stoppedMessage.setId(22L);
        when(messageService.saveUserMessage(eq(11L), eq(7L), anyString(), isNull(), any()))
                .thenReturn(userMessage);
        CountDownLatch stoppedSaved = new CountDownLatch(1);
        when(messageService.saveMessage(eq(11L), eq(7L), eq("assistant"), eq("对话已停止"), eq("text"),
                isNull(), isNull(), isNull())).thenAnswer(invocation -> {
                    stoppedSaved.countDown();
                    return stoppedMessage;
                });
        when(messageService.recentForContext(11L, 11)).thenReturn(List.of(userMessage));

        SysAiModel model = new SysAiModel();
        model.setId(9L);
        model.setModelType("chat");
        model.setIsActive(1);
        model.setApiProtocol("responses");
        model.setApiKey("encrypted");
        model.setBeanPerMtokenInput(1_000_000L);
        model.setBeanPerMtokenOutput(1_000_000L);
        when(modelMapper.selectOne(any(Wrapper.class))).thenReturn(model);
        when(encryptUtils.decrypt("encrypted")).thenReturn("key");
        when(promptAssembler.assembleSystemPrompt(eq(7L), isNull(), anyString()))
                .thenReturn(new PromptAssembler.AssembledPrompt("系统提示词", false));
        when(generationTaskService.submitImageBatch(eq(7L), eq(11L), eq(21L), any(), isNull(), eq("1:1"), any()))
                .thenReturn("task-1");
        when(beanDeductPort.getBalance(7L)).thenReturn(100L);
        when(beanDeductPort.deductUpToBalance(7L, 12L, "msg:22", "AI对话")).thenReturn(12L);

        String arguments = "{\"expected_count\":1,\"request_update\":\"REPLACE\","
                + "\"effective_request\":\"生成1张图\",\"jobs\":[{\"seq\":1,\"prompt\":\"海报\","
                + "\"reference_attachment_ids\":[],\"reference_bindings\":[],"
                + "\"generated_reference_seqs\":[]}],\"aspect_ratio\":\"1:1\"}";
        ResponsesApiClient.Round firstRound = new ResponsesApiClient.Round(
                "", List.of(new ResponsesApiClient.ToolCall("call-1", "generate_images", arguments)),
                objectMapper.createArrayNode(), 10L, 2L, 0L);
        CountDownLatch secondRoundStarted = new CountDownLatch(1);
        CountDownLatch keepBlocking = new CountDownLatch(1);
        when(responsesApiClient.streamRound(eq(model), eq("key"), any(), any(), any(), any()))
                .thenReturn(firstRound)
                .thenAnswer(invocation -> {
                    secondRoundStarted.countDown();
                    keepBlocking.await(5, TimeUnit.SECONDS);
                    return new ResponsesApiClient.Round(
                            "不应返回", List.of(), objectMapper.createArrayNode(), 1L, 1L, 0L);
                });

        AgentChatRequest request = new AgentChatRequest();
        request.setMessage("生成1张图");
        List<String> events = new CopyOnWriteArrayList<>();
        Disposable subscription = service.chatStream(7L, request).subscribe(events::add);

        assertTrue(secondRoundStarted.await(3, TimeUnit.SECONDS));
        assertTrue(events.stream().anyMatch(event ->
                event.contains("\"type\":\"tool\"") && event.contains("\"taskId\":\"task-1\"")));
        assertFalse(service.stopChat(8L, 21L));
        assertTrue(service.stopChat(7L, 21L));

        assertTrue(stoppedSaved.await(3, TimeUnit.SECONDS));
        assertFalse(service.stopChat(7L, 21L));
        verify(generationTaskService, times(1)).submitImageBatch(
                eq(7L), eq(11L), eq(21L), any(), isNull(), eq("1:1"), any());
        verify(responsesApiClient, times(2)).streamRound(eq(model), eq("key"), any(), any(), any(), any());
        verify(beanDeductPort, org.mockito.Mockito.timeout(3000).times(1)).deductUpToBalance(7L, 12L, "msg:22", "AI对话");
        verify(messageService, never()).saveMessage(eq(11L), eq(7L), eq("assistant"), anyString(),
                eq("structured"), anyString(), any(), any());
        subscription.dispose();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldContinueAndPersistResultAfterSubscriberDisconnects(boolean failUpstream) throws Exception {
        SysAiModelMapper modelMapper = mock(SysAiModelMapper.class);
        IConversationService conversationService = mock(IConversationService.class);
        IMessageService messageService = mock(IMessageService.class);
        PromptAssembler promptAssembler = mock(PromptAssembler.class);
        EncryptUtils encryptUtils = mock(EncryptUtils.class);
        ResponsesApiClient responsesApiClient = mock(ResponsesApiClient.class);
        IBeanDeductPort beanDeductPort = mock(IBeanDeductPort.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AgentService service = new AgentService(
                modelMapper, conversationService, messageService,
                mock(IMessageAttachmentService.class), mock(IReferenceScopeService.class),
                mock(IGenerationTaskService.class), promptAssembler,
                new ProfileStorage(Path.of("target/test-agent-disconnect-profile").toString(),
                        Path.of("target/test-agent-disconnect-home").toString()),
                encryptUtils, objectMapper, new BillingCalculator(), responsesApiClient,
                new ApiRateLimiter(50, 300, 3000));
        ReflectionTestUtils.setField(service, "beanDeductPort", beanDeductPort);
        ReflectionTestUtils.setField(service, "minTextBeans", 1L);

        Conversation conversation = new Conversation();
        conversation.setId(11L);
        when(conversationService.createConversation(eq(7L), anyString(), isNull())).thenReturn(conversation);

        Message userMessage = new Message();
        userMessage.setId(21L);
        Message assistantMessage = new Message();
        assistantMessage.setId(22L);
        when(messageService.saveUserMessage(eq(11L), eq(7L), anyString(), isNull(), any()))
                .thenReturn(userMessage);
        CountDownLatch assistantSaved = new CountDownLatch(1);
        String expectedReply = failUpstream ? "对话出错了,请稍后重试" : "完成";
        when(messageService.saveMessage(eq(11L), eq(7L), eq("assistant"), eq(expectedReply), eq("text"),
                isNull(), isNull(), isNull())).thenAnswer(invocation -> {
                    assistantSaved.countDown();
                    return assistantMessage;
                });
        when(messageService.recentForContext(11L, 11)).thenReturn(List.of(userMessage));

        SysAiModel model = new SysAiModel();
        model.setId(9L);
        model.setModelType("chat");
        model.setIsActive(1);
        model.setApiProtocol("responses");
        model.setApiKey("encrypted");
        model.setBeanPerMtokenInput(1_000_000L);
        model.setBeanPerMtokenOutput(1_000_000L);
        when(modelMapper.selectOne(any(Wrapper.class))).thenReturn(model);
        when(encryptUtils.decrypt("encrypted")).thenReturn("key");
        when(promptAssembler.assembleSystemPrompt(eq(7L), isNull(), anyString()))
                .thenReturn(new PromptAssembler.AssembledPrompt("系统提示词", false));
        when(beanDeductPort.getBalance(7L)).thenReturn(100L);

        CountDownLatch roundStarted = new CountDownLatch(1);
        when(beanDeductPort.deductUpToBalance(7L, 3L, "msg:22", "AI对话")).thenReturn(3L);
        CountDownLatch allowRoundToFinish = new CountDownLatch(1);
        when(responsesApiClient.streamRound(eq(model), eq("key"), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    roundStarted.countDown();
                    allowRoundToFinish.await(5, TimeUnit.SECONDS);
                    if (failUpstream) throw new IllegalStateException("上游内部错误，不应展示给用户");
                    return new ResponsesApiClient.Round(
                            "完成", List.of(), objectMapper.createArrayNode(), 2L, 1L, 0L);
                });

        AgentChatRequest request = new AgentChatRequest();
        request.setMessage("你好");
        Disposable subscription = service.chatStream(7L, request).subscribe();

        assertTrue(roundStarted.await(3, TimeUnit.SECONDS));
        subscription.dispose();
        allowRoundToFinish.countDown();

        assertTrue(assistantSaved.await(3, TimeUnit.SECONDS));
        if (failUpstream) {
            verify(beanDeductPort, never()).deductUpToBalance(anyLong(), anyLong(), anyString(), anyString());
        } else {
            verify(beanDeductPort, timeout(3000)).deductUpToBalance(7L, 3L, "msg:22", "AI对话");
        }
        verify(messageService, never()).saveMessage(eq(11L), eq(7L), eq("assistant"), eq("对话已停止"),
                anyString(), any(), any(), any());
        if (!failUpstream) assertFalse(service.stopChat(7L, 21L));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldConsumeClarificationScopeAndGenerateWithOriginalPromptAndImages() throws Exception {
        SysAiModelMapper modelMapper = mock(SysAiModelMapper.class);
        IConversationService conversationService = mock(IConversationService.class);
        IMessageService messageService = mock(IMessageService.class);
        IMessageAttachmentService attachmentService = mock(IMessageAttachmentService.class);
        IReferenceScopeService scopeService = mock(IReferenceScopeService.class);
        IGenerationTaskService generationTaskService = mock(IGenerationTaskService.class);
        PromptAssembler promptAssembler = mock(PromptAssembler.class);
        ProfileStorage profileStorage = mock(ProfileStorage.class);
        EncryptUtils encryptUtils = mock(EncryptUtils.class);
        ResponsesApiClient responsesApiClient = mock(ResponsesApiClient.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AgentService service = new AgentService(
                modelMapper, conversationService, messageService, attachmentService, scopeService,
                generationTaskService, promptAssembler, profileStorage, encryptUtils, objectMapper,
                new BillingCalculator(), responsesApiClient, new ApiRateLimiter(50, 300, 3000));
        ReflectionTestUtils.setField(service, "minTextBeans", 1L);

        Conversation conversation = new Conversation();
        conversation.setId(11L);
        when(conversationService.getOwned(11L, 7L)).thenReturn(conversation);

        Message current = new Message();
        current.setId(21L);
        current.setConversationId(11L);
        current.setUserId(7L);
        current.setRole("user");
        current.setContent("可以");
        when(messageService.saveUserMessage(eq(11L), eq(7L), eq("可以"), isNull(), any()))
                .thenReturn(current);

        Message source = new Message();
        source.setId(10L);
        source.setConversationId(11L);
        source.setUserId(7L);
        source.setRole("user");
        source.setContent("将图1和图2融合生成1张海报，人物使用图1，环境使用图2");
        when(messageService.getById(10L)).thenReturn(source);
        when(messageService.recentForContext(11L, 11)).thenReturn(List.of(source, current));

        ReferenceScope scope = new ReferenceScope();
        scope.setScopeId("scope-1");
        scope.setSourceMessageId(10L);
        scope.setAllowAdditionalImages(0);
        scope.setRoundNo(1);
        scope.setContextJson("{\"askedKeys\":[\"REFERENCE_MAPPING\",\"HARD_CONFLICT\"],"
                + "\"answers\":[{\"key\":\"REFERENCE_MAPPING\","
                + "\"question\":\"哪张是人物图？\",\"answer\":\"图1\","
                + "\"summary\":\"问题：哪张是人物图？\\n用户已回答：图1\"}],"
                + "\"pending\":{\"key\":\"HARD_CONFLICT\","
                + "\"question\":\"是否按原需求继续？\",\"imageUpdatePolicy\":\"KEEP\"}}");
        when(scopeService.claim("scope-1", 11L, 7L, 21L)).thenReturn(
                new ClaimedReferenceScope(scope, List.of("att-1", "att-2")));
        MessageAttachment first = attachment("att-1", "/profile/agent_reference/7/a.png");
        MessageAttachment second = attachment("att-2", "/profile/agent_reference/7/b.png");
        when(attachmentService.listOwnedOrdered(11L, 7L, List.of("att-1", "att-2")))
                .thenReturn(List.of(first, second));
        when(profileStorage.read(anyString())).thenReturn(new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47});

        SysAiModel model = new SysAiModel();
        model.setId(9L);
        model.setModelType("chat");
        model.setIsActive(1);
        model.setApiProtocol("responses");
        model.setApiKey("encrypted");
        when(modelMapper.selectOne(any(Wrapper.class))).thenReturn(model);
        when(encryptUtils.decrypt("encrypted")).thenReturn("key");
        when(promptAssembler.assembleSystemPrompt(eq(7L), isNull(), anyString()))
                .thenReturn(new PromptAssembler.AssembledPrompt("系统提示词", false));

        var argumentsNode = objectMapper.createObjectNode();
        argumentsNode.put("expected_count", 1);
        argumentsNode.put("request_update", "CONFIRM");
        argumentsNode.put("effective_request", source.getContent());
        argumentsNode.put("aspect_ratio", "1:1");
        var job = argumentsNode.putArray("jobs").addObject();
        job.put("seq", 1);
        job.put("prompt", "生成真实人物融入指定环境的单张海报，保持人物五官身份和场景结构。");
        job.putArray("reference_attachment_ids").add("att-1").add("att-2");
        job.putArray("generated_reference_seqs");
        var bindings = job.putArray("reference_bindings");
        addBinding(bindings, "original", "att-1", "primary", "identity");
        addBinding(bindings, "original", "att-2", "primary", "scene");
        String arguments = argumentsNode.toString();
        ResponsesApiClient.Round firstRound = new ResponsesApiClient.Round(
                "", List.of(new ResponsesApiClient.ToolCall("call-1", "generate_images", arguments)),
                objectMapper.createArrayNode(), 10L, 0L, 0L);
        ResponsesApiClient.Round secondRound = new ResponsesApiClient.Round(
                "已提交生成", List.of(), objectMapper.createArrayNode(), 2L, 0L, 1L);
        when(responsesApiClient.streamRound(eq(model), eq("key"), any(), any(), any(), any()))
                .thenReturn(firstRound, secondRound);
        when(generationTaskService.submitImageBatch(eq(7L), eq(11L), eq(21L), any(), isNull(), eq("1:1"), any()))
                .thenReturn("task-1");
        Message assistant = new Message();
        assistant.setId(22L);
        when(messageService.saveMessage(eq(11L), eq(7L), eq("assistant"), anyString(),
                eq("structured"), anyString(), isNull(), isNull())).thenReturn(assistant);

        AgentChatRequest request = new AgentChatRequest();
        request.setConversationId(11L);
        request.setMessage("可以");
        request.setClarificationScopeId("scope-1");
        List<String> events = service.chatStream(7L, request)
                .collectList().block(Duration.ofSeconds(5));

        assertTrue(events != null && events.stream().anyMatch(event -> event.contains("\"taskId\":\"task-1\"")));
        ArgumentCaptor<List<GenerateImageItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<String>> imagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(generationTaskService).submitImageBatch(eq(7L), eq(11L), eq(21L), itemsCaptor.capture(),
                isNull(), eq("1:1"), imagesCaptor.capture());
        GenerateImageItem item = itemsCaptor.getValue().get(0);
        assertTrue(item.getSourcePrompt().contains(source.getContent()));
        assertTrue(item.getSourcePrompt().contains("可以"));
        assertEquals(source.getContent(), item.getEffectiveRequest());
        assertEquals("CONFIRM", item.getRequestUpdate());
        assertEquals(5, item.getContractVersion());
        assertTrue(item.getPrompt().contains("本任务输入图1（上传素材）：主要依据：identity。"));
        assertTrue(item.getPrompt().contains("本任务输入图2（上传素材）：主要依据：scene。"));
        assertFalse(item.getPrompt().contains("将图1和图2融合"));
        assertFalse(item.getPrompt().contains(source.getContent()));
        assertFalse(item.getPrompt().contains("用户已回答：可以"));
        assertEquals(List.of(1, 2), item.getReferenceImageIndexes());
        assertEquals(List.of(first.getProfileUrl(), second.getProfileUrl()), imagesCaptor.getValue());
        ArgumentCaptor<String> runtimeCaptor = ArgumentCaptor.forClass(String.class);
        verify(promptAssembler).assembleSystemPrompt(eq(7L), isNull(), runtimeCaptor.capture());
        assertTrue(runtimeCaptor.getValue().contains("用户已回答：图1"));
        assertTrue(runtimeCaptor.getValue().contains("用户已回答：可以"));
        verify(scopeService).claim("scope-1", 11L, 7L, 21L);
        verify(scopeService, never()).cancel("scope-1");
    }

    @Test
    void shouldOpenOneShotScopeOnlyThroughClarificationTool() throws Exception {
        SysAiModelMapper modelMapper = mock(SysAiModelMapper.class);
        IConversationService conversationService = mock(IConversationService.class);
        IMessageService messageService = mock(IMessageService.class);
        IMessageAttachmentService attachmentService = mock(IMessageAttachmentService.class);
        IReferenceScopeService scopeService = mock(IReferenceScopeService.class);
        IGenerationTaskService generationTaskService = mock(IGenerationTaskService.class);
        PromptAssembler promptAssembler = mock(PromptAssembler.class);
        ProfileStorage profileStorage = mock(ProfileStorage.class);
        EncryptUtils encryptUtils = mock(EncryptUtils.class);
        ResponsesApiClient responsesApiClient = mock(ResponsesApiClient.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AgentService service = new AgentService(
                modelMapper, conversationService, messageService, attachmentService, scopeService,
                generationTaskService, promptAssembler, profileStorage, encryptUtils, objectMapper,
                new BillingCalculator(), responsesApiClient, new ApiRateLimiter(50, 300, 3000));
        ReflectionTestUtils.setField(service, "minTextBeans", 1L);

        Conversation conversation = new Conversation();
        conversation.setId(11L);
        when(conversationService.getOwned(11L, 7L)).thenReturn(conversation);
        Message current = new Message();
        current.setId(21L);
        current.setConversationId(11L);
        current.setUserId(7L);
        current.setRole("user");
        current.setContent("使用图1生成海报");
        AtomicReference<List<com.aurora.ai.agent.dto.MessageAttachmentDraft>> drafts = new AtomicReference<>();
        AtomicReference<String> storedMedia = new AtomicReference<>();
        when(messageService.saveUserMessage(eq(11L), eq(7L), eq("使用图1生成海报"),
                anyString(), any())).thenAnswer(invocation -> {
                    storedMedia.set(invocation.getArgument(3));
                    drafts.set(invocation.getArgument(4));
                    return current;
                });
        when(messageService.recentForContext(11L, 11)).thenReturn(List.of(current));
        when(profileStorage.read(anyString())).thenReturn(new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47});

        SysAiModel model = new SysAiModel();
        model.setId(9L);
        model.setModelType("chat");
        model.setIsActive(1);
        model.setApiProtocol("responses");
        model.setApiKey("encrypted");
        when(modelMapper.selectOne(any(Wrapper.class))).thenReturn(model);
        when(encryptUtils.decrypt("encrypted")).thenReturn("key");
        when(promptAssembler.assembleSystemPrompt(eq(7L), isNull(), anyString()))
                .thenReturn(new PromptAssembler.AssembledPrompt("系统提示词", false));

        ReferenceScope opened = new ReferenceScope();
        opened.setScopeId("scope-new");
        when(scopeService.open(eq(11L), eq(7L), eq(21L), any(), anyString(), eq(false),
                anyString(), eq(1), any()))
                .thenReturn(opened);
        when(responsesApiClient.streamRound(eq(model), eq("key"), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    String arguments = objectMapper.createObjectNode()
                            .put("key", "REFERENCE_MAPPING")
                            .put("question", "请确认是否保留原发型？")
                            .put("image_update_policy", "KEEP")
                            .toString();
                    return new ResponsesApiClient.Round(
                            "", List.of(new ResponsesApiClient.ToolCall(
                            "call-clarify", "request_clarification", arguments)),
                            objectMapper.createArrayNode(), 5L, 0L, 0L);
                });
        Message assistant = new Message();
        assistant.setId(22L);
        when(messageService.saveMessage(eq(11L), eq(7L), eq("assistant"), anyString(),
                eq("clarification"), anyString(), isNull(), isNull())).thenReturn(assistant);

        AgentChatRequest request = new AgentChatRequest();
        request.setConversationId(11L);
        request.setMessage("使用图1生成海报");
        request.setImages(List.of(
                "/profile/agent_reference/7/a.png",
                "/profile/agent_reference/7/b.png"));
        List<String> events = service.chatStream(7L, request)
                .collectList().block(Duration.ofSeconds(5));

        List<String> attachmentIds = drafts.get().stream()
                .map(com.aurora.ai.agent.dto.MessageAttachmentDraft::attachmentId)
                .toList();
        assertTrue(storedMedia.get().contains("\"type\":\"upload\""));
        assertTrue(storedMedia.get().contains("\"url\":\"/profile/agent_reference/7/a.png\""));
        assertTrue(storedMedia.get().contains("\"url\":\"/profile/agent_reference/7/b.png\""));
        verify(scopeService).open(eq(11L), eq(7L), eq(21L), eq(attachmentIds),
                eq("请确认是否保留原发型？"), eq(false), anyString(), eq(1), any());
        verify(scopeService, never()).cancel("scope-new");
        verifyNoInteractions(generationTaskService);
        assertTrue(events != null && !events.isEmpty());
        assertTrue(events.stream().anyMatch(event -> event.contains("\"type\":\"clarification\"")
                && event.contains("\"scopeId\":\"scope-new\"")));
        verify(messageService).saveMessage(eq(11L), eq(7L), eq("assistant"),
                eq("请确认是否保留原发型？"), eq("clarification"),
                anyString(), isNull(), isNull());
    }

    @Test
    void shouldRejectAnotherUsersUploadedReferenceBeforeCreatingConversation() {
        IConversationService conversationService = mock(IConversationService.class);
        AgentService service = new AgentService(
                mock(SysAiModelMapper.class), conversationService, mock(IMessageService.class),
                mock(IMessageAttachmentService.class), mock(IReferenceScopeService.class),
                mock(IGenerationTaskService.class), mock(PromptAssembler.class),
                mock(ProfileStorage.class), mock(EncryptUtils.class), new ObjectMapper(),
                new BillingCalculator(), mock(ResponsesApiClient.class),
                new ApiRateLimiter(50, 300, 3000));
        AgentImageInput image = new AgentImageInput();
        image.setType("upload");
        image.setUrl("/profile/agent_reference/8/not-owned.png");
        AgentChatRequest request = new AgentChatRequest();
        request.setMessage("使用这张图");
        request.setImageInputs(List.of(image));

        List<String> events = service.chatStream(7L, request)
                .collectList().block(Duration.ofSeconds(3));

        assertTrue(events != null && events.stream().anyMatch(event -> event.contains("不属于当前用户")));
        verifyNoInteractions(conversationService);
    }

    @Test
    void shouldEmitQueueNoticeAndBusyErrorBeforeCreatingConversation() {
        ApiRateLimiter limiter = new ApiRateLimiter(1, 0, 0);
        assertTrue(limiter.tryAcquire("chat"));
        IConversationService conversationService = mock(IConversationService.class);
        AgentService service = createQueuedService(limiter, conversationService);
        AgentChatRequest request = new AgentChatRequest();
        request.setMessage("你好");

        List<String> events = service.chatStream(7L, request)
                .collectList().block(Duration.ofSeconds(3));

        assertTrue(events != null && events.stream().anyMatch(event ->
                event.contains("\"type\":\"queue\"")
                        && event.contains("当前排队人数较多，请耐心等待")));
        assertTrue(events != null && events.stream().anyMatch(event ->
                event.contains("\"type\":\"error\"") && event.contains("系统繁忙，请稍后重试")));
        verifyNoInteractions(conversationService);
        limiter.release("chat");
    }

    @Test
    void shouldKeepWaitingAfterDisconnectAndStopByRequestId() throws Exception {
        ApiRateLimiter limiter = new ApiRateLimiter(1, 30, 60);
        assertTrue(limiter.tryAcquire("chat"));
        IConversationService conversationService = mock(IConversationService.class);
        AgentService service = createQueuedService(limiter, conversationService);
        AgentChatRequest request = new AgentChatRequest();
        request.setMessage("你好");
        request.setRequestId("request-queue");
        Disposable subscription = service.chatStream(7L, request).subscribe();

        long waitingDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (limiter.getQueueLength("chat") == 0 && System.nanoTime() < waitingDeadline) {
            Thread.onSpinWait();
        }
        assertEquals(1, limiter.getQueueLength("chat"));
        subscription.dispose();
        assertEquals(1, limiter.getQueueLength("chat"));
        assertTrue(service.stopChat(7L, "request-queue"));

        long cancelledDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (limiter.getQueueLength("chat") != 0 && System.nanoTime() < cancelledDeadline) {
            Thread.onSpinWait();
        }
        assertEquals(0, limiter.getQueueLength("chat"));
        assertEquals(0, limiter.getAvailablePermits("chat"));
        assertFalse(service.stopChat(7L, "request-queue"));
        verifyNoInteractions(conversationService);

        limiter.release("chat");
        assertEquals(1, limiter.getAvailablePermits("chat"));
    }

    private AgentService createQueuedService(ApiRateLimiter limiter,
                                             IConversationService conversationService) {
        return new AgentService(
                mock(SysAiModelMapper.class), conversationService, mock(IMessageService.class),
                mock(IMessageAttachmentService.class), mock(IReferenceScopeService.class),
                mock(IGenerationTaskService.class), mock(PromptAssembler.class),
                new ProfileStorage(Path.of("target/test-agent-queue-profile").toString(),
                        Path.of("target/test-agent-queue-home").toString()),
                mock(EncryptUtils.class), new ObjectMapper(), new BillingCalculator(),
                mock(ResponsesApiClient.class), limiter);
    }

    private SysAiModel responsesModel() {
        SysAiModel model = new SysAiModel();
        model.setId(9L);
        model.setModelType("chat");
        model.setIsActive(1);
        model.setApiProtocol("responses");
        model.setApiKey("encrypted");
        return model;
    }

    private String imagePlanArguments(ObjectMapper objectMapper, int expectedCount, int jobCount) {
        return imagePlanArguments(objectMapper, expectedCount, jobCount, "生成" + expectedCount + "张图", false);
    }

    private String imagePlanArguments(ObjectMapper objectMapper, int expectedCount, int jobCount,
                                      String effectiveRequest, boolean consistentSubject) {
        var args = objectMapper.createObjectNode();
        args.put("expected_count", expectedCount);
        args.put("request_update", "REPLACE");
        args.put("effective_request", effectiveRequest);
        var jobs = args.putArray("jobs");
        for (int i = 1; i <= jobCount; i++) {
            var job = jobs.addObject();
            job.put("seq", i);
            job.put("prompt", "独立摄影构图方案" + (char) ('A' + i - 1));
            job.putArray("reference_attachment_ids");
            var generated = job.putArray("generated_reference_seqs");
            var bindings = job.putArray("reference_bindings");
        }
        return args.toString();
    }

    private void addBinding(ArrayNode bindings, String sourceType, String sourceId,
                            String usage, String... attributes) {
        var binding = bindings.addObject();
        binding.put("source_type", sourceType);
        binding.put("source_id", sourceId);
        binding.put("usage", usage);
        var attributeValues = binding.putArray("attributes");
        for (String attribute : attributes) {
            attributeValues.add(attribute);
        }
    }

    private ResponsesApiClient.Round toolRound(ObjectMapper objectMapper, String callId, String arguments) {
        return new ResponsesApiClient.Round(
                "", List.of(new ResponsesApiClient.ToolCall(callId, "generate_images", arguments)),
                objectMapper.createArrayNode(), 2L, 0L, 1L);
    }

    private String affordablePlanContext(ObjectMapper objectMapper, int requestedCount, int affordableCount) {
        var root = objectMapper.createObjectNode();
        var pending = root.putObject("pending");
        pending.put("key", "INSUFFICIENT_BALANCE");
        pending.put("question", "余额不足");
        pending.put("allowFreeText", false);
        pending.put("imageUpdatePolicy", "KEEP");
        var plan = root.putObject("affordablePlan");
        plan.put("requestedCount", requestedCount);
        plan.put("affordableCount", affordableCount);
        var items = plan.putArray("items");
        for (int i = 1; i <= requestedCount; i++) {
            var item = items.addObject();
            item.put("seq", i);
            item.put("prompt", "第 " + i + " 张图");
            item.putArray("referenceImageIndexes");
            item.putArray("generatedReferenceSeqs");
        }
        return root.toString();
    }

    private MessageAttachment attachment(String attachmentId, String url) {
        MessageAttachment attachment = new MessageAttachment();
        attachment.setAttachmentId(attachmentId);
        attachment.setProfileUrl(url);
        return attachment;
    }
}
