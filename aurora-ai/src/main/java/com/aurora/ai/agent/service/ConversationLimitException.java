package com.aurora.ai.agent.service;

import com.aurora.common.core.exception.ServiceException;

/** A definitive rejection before model work, with an explicit next action for mobile clients. */
public class ConversationLimitException extends ServiceException {
    public static final String CODE = "CONVERSATION_TOO_LONG";
    public static final String ACTION = "NEW_CONVERSATION";
    public static final String MESSAGE = "本会话内容较多，请从会话列表新建对话继续。旧记录仍可查看，新会话请重新选择参考图。";

    public ConversationLimitException() {
        super(MESSAGE);
    }
}
