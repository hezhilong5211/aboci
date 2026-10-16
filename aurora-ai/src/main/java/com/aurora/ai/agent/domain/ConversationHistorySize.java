package com.aurora.ai.agent.domain;

import lombok.Data;

/** Aggregate only; measuring a conversation must not load its full contents. */
@Data
public class ConversationHistorySize {
    private long messageCount;
    private long characterCount;
    private long imageIndexCharacters;
}
