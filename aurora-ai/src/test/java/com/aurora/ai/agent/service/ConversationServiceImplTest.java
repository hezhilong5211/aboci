package com.aurora.ai.agent.service;

import com.aurora.ai.agent.domain.Conversation;
import com.aurora.ai.agent.service.impl.ConversationServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class ConversationServiceImplTest {

    @Test
    void touchExtendsExpiryFromLastUseTime() {
        ConversationServiceImpl service = spy(new ConversationServiceImpl(30));
        doReturn(true).when(service).updateById(any(Conversation.class));

        service.touch(19L);

        ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
        verify(service).updateById(captor.capture());
        Conversation updated = captor.getValue();
        assertThat(updated.getId()).isEqualTo(19L);
        assertThat(ChronoUnit.DAYS.between(updated.getLastMessageTime(), updated.getExpireTime()))
                .isEqualTo(30);
    }

    @Test
    void expiredConversationIsNoLongerOwned() {
        ConversationServiceImpl service = spy(new ConversationServiceImpl(30));
        Conversation expired = new Conversation();
        expired.setId(8L);
        expired.setUserId(3L);
        expired.setExpireTime(LocalDateTime.now().minusSeconds(1));
        doReturn(expired).when(service).getById(8L);

        assertThat(service.getOwned(8L, 3L)).isNull();
    }
}
