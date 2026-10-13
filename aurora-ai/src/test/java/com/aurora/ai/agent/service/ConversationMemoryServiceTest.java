package com.aurora.ai.agent.service;

import com.aurora.ai.agent.domain.Conversation;
import com.aurora.ai.agent.domain.ConversationMemory;
import com.aurora.ai.agent.domain.Message;
import com.aurora.ai.agent.mapper.ConversationMemoryMapper;
import com.aurora.ai.agent.mapper.MessageAttachmentMapper;
import com.aurora.ai.agent.mapper.MessageMapper;
import com.aurora.ai.domain.SysAiModel;
import com.aurora.ai.provider.ResponsesApiClient;
import com.aurora.common.core.exception.ServiceException;
import com.aurora.framework.utils.EncryptUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ConversationMemoryServiceTest {
    private final ConversationMemoryMapper memories = mock(ConversationMemoryMapper.class);
    private final MessageMapper messages = mock(MessageMapper.class);
    private final MessageAttachmentMapper attachments = mock(MessageAttachmentMapper.class);
    private final IConversationService conversations = mock(IConversationService.class);
    private final ResponsesApiClient responses = mock(ResponsesApiClient.class);
    private final EncryptUtils encryption = mock(EncryptUtils.class);
    private final ObjectMapper json = new ObjectMapper();
    private final ConversationMemoryService service = new ConversationMemoryService(memories, messages,
            attachments, conversations, responses, encryption, json, 2, 1000, 2000, 1000);

    @Test
    void olderRequirementsAreCompressedAndRecentEditsRemainVerbatim() throws Exception {
        when(conversations.getOwned(11L, 7L)).thenReturn(new Conversation());
        Message earlier = message(1L, "user", "做三张海报，确认主色为红色");
        Message change = message(12L, "user", "改成两张，主色改蓝色");
        when(messages.selectList(any())).thenReturn(List.of(change), List.of(earlier), List.of(), List.of());
        when(attachments.selectList(any())).thenReturn(List.of());
        when(encryption.decrypt("enc")).thenReturn("key");
        when(responses.streamRound(any(), eq("key"), any(), any(), any(), any())).thenReturn(new ResponsesApiClient.Round(
                "{\"summary\":\"早期海报要求\",\"requirements\":{\"count\":3,\"color\":\"红色\"},\"confirmedOptions\":[\"红色\"]}",
                List.of(), json.createArrayNode(), 10L, 10L, 0L));
        ConversationMemoryService.Context context = service.prepare(7L, 11L, 13L, model());
        assertTrue(context.memoryPrompt().contains("早期海报要求"));
        assertEquals(List.of(change), context.recentMessages());
        assertEquals("改成两张，主色改蓝色", context.recentMessages().get(0).getContent());
        ArgumentCaptor<ConversationMemory> saved = ArgumentCaptor.forClass(ConversationMemory.class);
        verify(memories).insert(saved.capture());
        assertEquals(1L, saved.getValue().getCoveredMessageId());
        assertEquals(7L, saved.getValue().getUserId());
        assertTrue(context.memoryPrompt().contains("当前用户需求和最新澄清优先"));
    }

    @Test
    void failedSummaryKeepsOlderOriginalMessagesAndDoesNotAdvanceMemoryCursor() throws Exception {
        when(conversations.getOwned(11L, 7L)).thenReturn(new Conversation());
        Message earlier = message(1L, "user", "保留包装文案 ABC123");
        Message recent = message(12L, "user", "继续修改");
        when(messages.selectList(any())).thenReturn(List.of(recent), List.of(earlier), List.of(earlier));
        when(attachments.selectList(any())).thenReturn(List.of());
        when(encryption.decrypt("enc")).thenReturn("key");
        when(responses.streamRound(any(), anyString(), any(), any(), any(), any())).thenThrow(new IllegalStateException("failed"));
        ConversationMemoryService.Context context = service.prepare(7L, 11L, 13L, model());
        assertEquals(List.of(earlier, recent), context.recentMessages());
        verify(memories, never()).insert(any(ConversationMemory.class));
        verify(memories, never()).updateById(any(ConversationMemory.class));
    }

    @Test
    void priorGeneratedImageGetsStableIndexWithoutBecomingAnInputReference() {
        when(conversations.getOwned(11L, 7L)).thenReturn(new Conversation());
        Message generated = message(12L, "assistant", "生成完成");
        generated.setMedia("[{\"taskId\":\"task-old\",\"seq\":2}]");
        when(messages.selectList(any())).thenReturn(List.of(generated), List.of(), List.of());
        when(attachments.selectList(any())).thenReturn(List.of());
        ConversationMemoryService.Context context = service.prepare(7L, 11L, 13L, model());
        assertTrue(context.memoryPrompt().contains("task-old"));
        assertTrue(context.memoryPrompt().contains("不得把所有历史图片自动作为参考"));
        assertEquals(generated.getMedia(), context.recentMessages().get(0).getMedia());
        verifyNoInteractions(responses);
    }

    @Test
    void rejectsMemoryFromAnotherUserBeforeAnyModelCall() {
        when(conversations.getOwned(11L, 7L)).thenReturn(new Conversation());
        ConversationMemory foreign = new ConversationMemory();
        foreign.setUserId(8L);
        when(memories.selectById(11L)).thenReturn(foreign);
        assertThrows(ServiceException.class, () -> service.prepare(7L, 11L, 13L, model()));
        verifyNoInteractions(messages, responses);
    }

    private Message message(Long id, String role, String text) {
        Message message = new Message();
        message.setId(id);
        message.setConversationId(11L);
        message.setUserId(7L);
        message.setRole(role);
        message.setContent(text);
        return message;
    }

    private SysAiModel model() {
        SysAiModel model = new SysAiModel();
        model.setId(9L);
        model.setApiProtocol("responses");
        model.setApiKey("enc");
        return model;
    }
}
