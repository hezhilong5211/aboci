package com.aurora.ai.agent.retention;

import com.aurora.ai.agent.domain.Conversation;
import com.aurora.ai.agent.mapper.ConversationMapper;
import com.aurora.ai.agent.service.IConversationService;
import com.aurora.ai.agent.service.IMessageService;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationRetentionServiceTest {

    @Test
    void deletesMessagesAndConversationInsideTransaction() {
        IConversationService conversationService = mock(IConversationService.class);
        IMessageService messageService = mock(IMessageService.class);
        ConversationMapper mapper = mock(ConversationMapper.class);
        TransactionTemplate transactionTemplate = executingTransactionTemplate();
        Conversation expired = expiredConversation(7L);
        when(conversationService.list(any(Wrapper.class))).thenReturn(List.of(expired));
        when(mapper.selectExpiredForUpdate(any(), any())).thenReturn(expired);
        when(mapper.deletePermanentlyById(7L)).thenReturn(1);

        ConversationRetentionService service = new ConversationRetentionService(
                conversationService, messageService,
                mock(com.aurora.ai.agent.service.IMessageAttachmentService.class),
                mock(com.aurora.ai.agent.service.IReferenceScopeService.class),
                mapper, transactionTemplate, 100);

        assertThat(service.cleanupExpired()).isEqualTo(1);
        verify(messageService).remove(any(Wrapper.class));
        verify(mapper).deletePermanentlyById(7L);
    }

    @Test
    void skipsConversationThatWasExtendedBeforeLock() {
        IConversationService conversationService = mock(IConversationService.class);
        IMessageService messageService = mock(IMessageService.class);
        ConversationMapper mapper = mock(ConversationMapper.class);
        Conversation expired = expiredConversation(9L);
        when(conversationService.list(any(Wrapper.class))).thenReturn(List.of(expired));
        when(mapper.selectExpiredForUpdate(any(), any())).thenReturn(null);

        ConversationRetentionService service = new ConversationRetentionService(
                conversationService, messageService,
                mock(com.aurora.ai.agent.service.IMessageAttachmentService.class),
                mock(com.aurora.ai.agent.service.IReferenceScopeService.class),
                mapper, executingTransactionTemplate(), 100);

        assertThat(service.cleanupExpired()).isZero();
        verify(messageService, never()).remove(any(Wrapper.class));
        verify(mapper, never()).deletePermanentlyById(any());
    }

    private TransactionTemplate executingTransactionTemplate() {
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        doAnswer(invocation -> {
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
        return transactionTemplate;
    }

    private Conversation expiredConversation(Long id) {
        Conversation conversation = new Conversation();
        conversation.setId(id);
        conversation.setExpireTime(LocalDateTime.now().minusDays(1));
        return conversation;
    }
}
