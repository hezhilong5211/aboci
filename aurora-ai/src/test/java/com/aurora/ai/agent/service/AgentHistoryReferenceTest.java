package com.aurora.ai.agent.service;

import com.aurora.ai.agent.billing.BillingCalculator;
import com.aurora.ai.agent.domain.GenerationTask;
import com.aurora.ai.agent.domain.Message;
import com.aurora.ai.agent.dto.MessageAttachmentDraft;
import com.aurora.ai.mapper.SysAiModelMapper;
import com.aurora.ai.provider.ResponsesApiClient;
import com.aurora.framework.mq.ApiRateLimiter;
import com.aurora.framework.storage.ProfileStorage;
import com.aurora.framework.utils.EncryptUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentHistoryReferenceTest {
    private final IMessageService messages = mock(IMessageService.class);
    private final IGenerationTaskService generations = mock(IGenerationTaskService.class);
    private final ProfileStorage storage = mock(ProfileStorage.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final AgentService service = new AgentService(mock(SysAiModelMapper.class), mock(IConversationService.class),
            messages, mock(IMessageAttachmentService.class), mock(IReferenceScopeService.class), generations,
            mock(PromptAssembler.class), storage, mock(EncryptUtils.class), mapper, new BillingCalculator(),
            mock(ResponsesApiClient.class), new ApiRateLimiter(50, 300, 3000));

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void explicitOlderImageSelectionPreservesSlotAndLoadsActualImageBeforePlanning() throws Exception {
        source("用之前生成那张继续改，改成蓝色背景");
        when(generations.getOwned("older-task", 7L)).thenReturn(task(11L));
        when(storage.read("/profile/generated/second.png")).thenReturn(new byte[]{1, 2, 3});
        Object state = state();
        String result = select(2, state);
        assertEquals("SELECTED", mapper.readTree(result).path("status").asText(), result);
        ArgumentCaptor<List> drafts = ArgumentCaptor.forClass(List.class);
        verify(messages).appendUserAttachments(eq(7L), eq(21L), anyString(), drafts.capture());
        MessageAttachmentDraft draft = (MessageAttachmentDraft) drafts.getValue().get(0);
        assertEquals("older-task", draft.sourceTaskId());
        assertEquals(2, draft.sourceSeq());
        var input = mapper.createArrayNode();
        ReflectionTestUtils.invokeMethod(service, "appendSelectedHistory", input, state, true);
        String multimodal = input.toString();
        assertTrue(multimodal.contains("input_image"));
        assertTrue(multimodal.contains("data:image/"));
        assertTrue(multimodal.contains(draft.attachmentId()));
        assertFalse((boolean) ReflectionTestUtils.getField(state, "historyPending"));
    }

    @Test
    void failedSlotIsNotReplacedByAnotherSuccessfulImage() throws Exception {
        source("用之前生成的第一张图继续改");
        when(generations.getOwned("older-task", 7L)).thenReturn(task(11L));
        String result = select(1, state());
        assertTrue(result.contains("失败或已失效"));
        verify(messages, never()).appendUserAttachments(anyLong(), anyLong(), anyString(), anyList());
        verifyNoInteractions(storage);
    }

    @Test
    void sameUserImageFromOtherConversationCannotEnterMemoryReferences() throws Exception {
        source("用之前生成那张图继续改");
        when(generations.getOwned("older-task", 7L)).thenReturn(task(12L));
        assertTrue(select(2, state()).contains("不属于本会话"));
        verify(messages, never()).appendUserAttachments(anyLong(), anyLong(), anyString(), anyList());
        verifyNoInteractions(storage);
    }

    @Test
    void historicalImagesAreNeverAutomaticallyLoadedForANewRequest() throws Exception {
        source("画一张蓝色海报");
        assertTrue(select(2, state()).contains("没有明确引用历史图片"));
        verifyNoInteractions(generations, storage);
    }

    private String select(int seq, Object state) throws Exception {
        JsonNode args = mapper.readTree("{\"generated_images\":[{\"task_id\":\"older-task\",\"seq\":" + seq + "}]}");
        return ReflectionTestUtils.invokeMethod(service, "selectHistoryImages", args, 7L, 11L, 21L, context(), state);
    }

    private Object context() throws Exception {
        Class<?> type = Class.forName(AgentService.class.getName() + "$ReferenceContext");
        Constructor<?> constructor = type.getDeclaredConstructor(Long.class, String.class, List.class,
                boolean.class, String.class, int.class, Set.class, String.class, String.class);
        constructor.setAccessible(true);
        return constructor.newInstance(21L, "用之前生成那张图继续改", List.of(), false, null, 0, Set.of(), null, null);
    }

    private Object state() throws Exception {
        Class<?> type = Class.forName(AgentService.class.getName() + "$ToolExecutionState");
        Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private void source(String content) {
        Message source = new Message();
        source.setId(21L);
        source.setUserId(7L);
        source.setConversationId(11L);
        source.setRole("user");
        source.setContent(content);
        when(messages.getById(21L)).thenReturn(source);
    }

    private GenerationTask task(Long conversationId) {
        GenerationTask task = new GenerationTask();
        task.setTaskId("older-task");
        task.setUserId(7L);
        task.setConversationId(conversationId);
        task.setMessageId(10L);
        task.setStatus("SUCCEEDED");
        task.setResultUrls("[null,\"/profile/generated/second.png\"]");
        return task;
    }
}
