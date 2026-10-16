package com.aurora.ai.agent.service;

import com.aurora.ai.agent.domain.ConversationHistorySize;
import com.aurora.ai.agent.mapper.MessageMapper;
import com.aurora.common.core.exception.ServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Prevents repeatedly spending model time on conversations that need a fresh context. */
@Service
public class ConversationCapacityService {
    private final MessageMapper messages;
    private final IConversationService conversations;
    private final long maxMessages;
    private final long maxCharacters;
    private final long maxImageIndexCharacters;

    public ConversationCapacityService(MessageMapper messages, IConversationService conversations,
                                       @Value("${ai.conversation.max-messages:200}") long maxMessages,
                                       @Value("${ai.conversation.max-characters:100000}") long maxCharacters,
                                       @Value("${ai.conversation.max-image-index-characters:60000}") long maxImageIndexCharacters) {
        this.messages = messages;
        this.conversations = conversations;
        this.maxMessages = Math.max(2, maxMessages);
        this.maxCharacters = Math.max(2000, maxCharacters);
        this.maxImageIndexCharacters = Math.max(2000, maxImageIndexCharacters);
    }

    public void requireCapacity(Long userId, Long conversationId) {
        if (conversations.getOwned(conversationId, userId) == null) {
            throw new ServiceException("会话不存在或无权访问");
        }
        ConversationHistorySize size = messages.historySize(userId, conversationId);
        if (size != null && (size.getMessageCount() >= maxMessages
                || size.getCharacterCount() >= maxCharacters
                || size.getImageIndexCharacters() >= maxImageIndexCharacters)) {
            throw new ConversationLimitException();
        }
    }
}
