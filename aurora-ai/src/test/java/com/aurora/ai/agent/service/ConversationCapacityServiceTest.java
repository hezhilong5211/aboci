package com.aurora.ai.agent.service;

import com.aurora.ai.agent.domain.Conversation;
import com.aurora.ai.agent.domain.ConversationHistorySize;
import com.aurora.ai.agent.mapper.MessageMapper;
import com.aurora.common.core.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConversationCapacityServiceTest {
    private final MessageMapper messages = mock(MessageMapper.class);
    private final IConversationService conversations = mock(IConversationService.class);
    private final ConversationCapacityService service = new ConversationCapacityService(messages, conversations,
            200, 100000, 60000);

    @ParameterizedTest
    @CsvSource({"200,100,0", "674,527603,301092", "20,100000,0", "20,100,60000"})
    void oversizedHistoryRequiresNewConversationWithoutLoadingMessageBodies(long count, long chars, long index) {
        when(conversations.getOwned(11L, 7L)).thenReturn(new Conversation());
        when(messages.historySize(7L, 11L)).thenReturn(size(count, chars, index));
        var exception = assertThrows(ConversationLimitException.class, () -> service.requireCapacity(7L, 11L));
        assertTrue(exception.getMessage().contains("新建对话"));
        assertTrue(exception.getMessage().contains("旧记录仍可查看"));
        verify(messages).historySize(7L, 11L);
        verifyNoMoreInteractions(messages);
    }

    @ParameterizedTest
    @CsvSource({"0,0,0", "199,99999,59999", "34,26714,10000"})
    void normalConversationCanContinue(long count, long chars, long index) {
        when(conversations.getOwned(11L, 7L)).thenReturn(new Conversation());
        when(messages.historySize(7L, 11L)).thenReturn(size(count, chars, index));
        assertDoesNotThrow(() -> service.requireCapacity(7L, 11L));
    }

    @Test
    void foreignConversationIsRejectedBeforeReadingItsSize() {
        assertThrows(ServiceException.class, () -> service.requireCapacity(7L, 11L));
        verifyNoInteractions(messages);
    }

    private ConversationHistorySize size(long count, long chars, long index) {
        var size = new ConversationHistorySize(); size.setMessageCount(count);
        size.setCharacterCount(chars); size.setImageIndexCharacters(index); return size;
    }
}
