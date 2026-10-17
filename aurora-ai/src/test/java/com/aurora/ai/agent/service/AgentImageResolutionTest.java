package com.aurora.ai.agent.service;

import com.aurora.ai.agent.billing.BillingCalculator;
import com.aurora.ai.agent.billing.IBeanDeductPort;
import com.aurora.ai.agent.domain.Conversation;
import com.aurora.ai.agent.domain.Message;
import com.aurora.ai.agent.dto.AgentChatRequest;
import com.aurora.ai.agent.dto.GenerationOptionsVO;
import com.aurora.ai.domain.SysAiModel;
import com.aurora.ai.mapper.SysAiModelMapper;
import com.aurora.ai.provider.ResponsesApiClient;
import com.aurora.framework.mq.ApiRateLimiter;
import com.aurora.framework.storage.ProfileStorage;
import com.aurora.framework.utils.EncryptUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentImageResolutionTest {
    private final SysAiModelMapper models = mock(SysAiModelMapper.class);
    private final IConversationService conversations = mock(IConversationService.class);
    private final IMessageService messages = mock(IMessageService.class);
    private final IGenerationTaskService generations = mock(IGenerationTaskService.class);
    private final PromptAssembler prompts = mock(PromptAssembler.class);
    private final EncryptUtils encryption = mock(EncryptUtils.class);
    private final ResponsesApiClient responses = mock(ResponsesApiClient.class);
    private final IBeanDeductPort beans = mock(IBeanDeductPort.class);
    private final ObjectMapper json = new ObjectMapper();
    private final AgentService service = new AgentService(models, conversations, messages,
            mock(IMessageAttachmentService.class), mock(IReferenceScopeService.class), generations,
            prompts, mock(ProfileStorage.class), encryption, json, new BillingCalculator(), responses,
            new ApiRateLimiter(50, 300, 3000));

    @Test
    void unsupportedResolutionFailsBeforeRunReservationMessagesModelCallsOrCharging() {
        ChatRunService runs = mock(ChatRunService.class);
        ReflectionTestUtils.setField(service, "chatRunService", runs);
        ReflectionTestUtils.setField(service, "beanDeductPort", beans);
        Conversation conversation = new Conversation();
        conversation.setId(11L);
        when(conversations.getOwned(11L, 7L)).thenReturn(conversation);
        SysAiModel model = model();
        when(models.selectById(19L)).thenReturn(model);
        GenerationOptionsVO options = new GenerationOptionsVO(List.of("1K", "2K"), "1K", Map.of());
        options.setModelId(17L);
        when(generations.getGenerationOptions(17L)).thenReturn(options);
        AgentChatRequest request = request("4K");

        List<String> events = service.chatStream(7L, request).collectList().block(Duration.ofSeconds(2));

        assertThat(events).singleElement().asString().contains("所选分辨率未在后台启用");
        verify(runs, never()).reserve(anyLong(), anyLong(), anyString(), anyString(), any(), any(), any(), any());
        verifyNoInteractions(messages, responses, beans);
    }

    @Test
    void selectedResolutionReachesImageBatchInsteadOfModelDefault() throws Exception {
        Conversation conversation = new Conversation(); conversation.setId(11L);
        when(conversations.getOwned(11L, 7L)).thenReturn(conversation);
        Message user = new Message(); user.setId(21L); user.setContent("生成一张海报");
        when(messages.saveUserMessage(eq(11L), eq(7L), eq("生成一张海报"), isNull(), any())).thenReturn(user);
        when(messages.recentForContext(11L, 11)).thenReturn(List.of(user));
        Message assistant = new Message(); assistant.setId(22L);
        when(messages.saveMessage(eq(11L), eq(7L), eq("assistant"), anyString(), anyString(), any(), any(), any()))
                .thenReturn(assistant);
        SysAiModel model = model();
        when(models.selectById(19L)).thenReturn(model);
        when(encryption.decrypt("encrypted")).thenReturn("key");
        when(prompts.assembleSystemPrompt(eq(7L), isNull(), anyString()))
                .thenReturn(new PromptAssembler.AssembledPrompt("系统提示词", false));
        String plan = "{\"expected_count\":1,\"request_update\":\"REPLACE\","
                + "\"effective_request\":\"生成一张海报\",\"jobs\":[{\"seq\":1,"
                + "\"prompt\":\"生成一张摄影海报\",\"reference_attachment_ids\":[],"
                + "\"reference_bindings\":[]}],\"aspect_ratio\":\"3:4\"}";
        when(responses.streamRound(eq(model), eq("key"), any(), any(), any(), any()))
                .thenReturn(new ResponsesApiClient.Round("", List.of(new ResponsesApiClient.ToolCall(
                        "resolution-call", "generate_images", plan)), json.createArrayNode(), 0, 0, 0))
                .thenReturn(new ResponsesApiClient.Round("已提交", List.of(), json.createArrayNode(), 0, 0, 0));
        when(generations.submitImageBatch(eq(7L), eq(11L), eq(21L), any(), eq("4K"), eq("3:4"),
                eq(List.of()), eq(17L), anyString())).thenReturn("selected-resolution-task");

        List<String> events = service.chatStream(7L, request("4K")).collectList().block(Duration.ofSeconds(5));

        verify(generations).submitImageBatch(eq(7L), eq(11L), eq(21L), any(), eq("4K"), eq("3:4"),
                eq(List.of()), eq(17L), anyString());
        assertThat(events).anyMatch(event -> event.contains("selected-resolution-task"));
        assertThat(events).noneMatch(event -> event.contains("\"type\":\"error\""));
    }

    @Test
    void fingerprintPreservesLegacyRetriesAndRejectsResolutionChangeForSameRequest() throws Exception {
        AgentChatRequest request = request(null);
        ObjectNode legacy = json.valueToTree(request);
        legacy.remove(List.of("requestId", "conversationId", "imageResolution"));
        String previous = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(legacy.toString().getBytes(StandardCharsets.UTF_8)));
        String current = ReflectionTestUtils.invokeMethod(service, "requestFingerprint", request);
        assertThat(current).isEqualTo(previous);
        request.setImageResolution("4K");
        String fourK = ReflectionTestUtils.invokeMethod(service, "requestFingerprint", request);
        request.setImageResolution("2K");
        String twoK = ReflectionTestUtils.invokeMethod(service, "requestFingerprint", request);
        assertThat(fourK).isNotEqualTo(current).isNotEqualTo(twoK);
    }

    private AgentChatRequest request(String resolution) {
        AgentChatRequest request = new AgentChatRequest();
        request.setRequestId("resolution-test");
        request.setConversationId(11L);
        request.setModelId(19L);
        request.setImageModelId(17L);
        request.setImageResolution(resolution);
        request.setMessage("生成一张海报");
        return request;
    }

    private SysAiModel model() {
        SysAiModel model = new SysAiModel(); model.setId(19L);
        model.setModelType("chat"); model.setIsActive(1);
        model.setApiProtocol("responses"); model.setApiKey("encrypted");
        return model;
    }
}
