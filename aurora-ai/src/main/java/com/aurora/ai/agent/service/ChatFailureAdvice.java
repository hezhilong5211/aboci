package com.aurora.ai.agent.service;

import com.aurora.ai.agent.billing.InsufficientBeanException;
import com.aurora.common.core.exception.ServiceException;
import org.springframework.util.StringUtils;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.TimeoutException;

/** Shared failure contract for live events, persisted snapshots and duplicate-request replay. */
public record ChatFailureAdvice(String message, String errorCode, String action) {
    public static final String NEW_CONVERSATION = "NEW_CONVERSATION";
    public static final String CONTINUE_MESSAGE =
            "可新建会话继续，旧记录和已提交图片仍保留；新会话请重新选择参考图。";
    public static final String FAILED_MESSAGE = "本轮对话未完成。" + CONTINUE_MESSAGE;
    public static final String TIMEOUT_MESSAGE = "本轮对话响应超时。" + CONTINUE_MESSAGE + "若仍超时，请稍后再试。";
    public static final String INTERRUPTED_MESSAGE = "本轮对话已中断。" + CONTINUE_MESSAGE;
    private static final String RECHARGE_MESSAGE = "豆子余额不足,充值后可返回继续生成~";

    /** Input validation is deliberately excluded until the model orchestration stage starts. */
    public static ChatFailureAdvice fromFailure(Exception failure, boolean orchestrationStarted) {
        if (failure instanceof InsufficientBeanException) return recharge(RECHARGE_MESSAGE);
        if (failure instanceof ConversationLimitException) {
            return new ChatFailureAdvice(ConversationLimitException.MESSAGE,
                    ConversationLimitException.CODE, NEW_CONVERSATION);
        }
        String message = failure instanceof ServiceException && StringUtils.hasText(failure.getMessage())
                ? failure.getMessage() : "对话出错了,请稍后重试";
        if (!orchestrationStarted) return new ChatFailureAdvice(message, null, null);
        if (isTimeout(failure)) return new ChatFailureAdvice(TIMEOUT_MESSAGE, "CHAT_MODEL_TIMEOUT", NEW_CONVERSATION);
        if (!(failure instanceof ServiceException)) message = FAILED_MESSAGE;
        else {
            // ai_chat_run.error_message is VARCHAR(500); leave room for the complete recovery guidance.
            int reasonLimit = 500 - CONTINUE_MESSAGE.length() - 1;
            if (message.length() > reasonLimit) message = message.substring(0, reasonLimit - 1) + "…";
            message += "\n" + CONTINUE_MESSAGE;
        }
        return new ChatFailureAdvice(message, "CHAT_FAILED", NEW_CONVERSATION);
    }

    /** No schema change: recognize our durable messages and known legacy failures only. */
    public static ChatFailureAdvice restore(String status, String completionStatus, String message) {
        if ("CANCELLED".equals(status) || "CANCELLED".equals(completionStatus)) return unchanged(message);
        if ("WAITING_RECHARGE".equals(status) || "WAITING_RECHARGE".equals(completionStatus)) {
            return recharge(StringUtils.hasText(message) ? message : RECHARGE_MESSAGE);
        }
        if ("INTERRUPTED".equals(status) || "INTERRUPTED".equals(completionStatus)) {
            return new ChatFailureAdvice(INTERRUPTED_MESSAGE, "CHAT_INTERRUPTED", NEW_CONVERSATION);
        }
        if (!"FAILED".equals(status) && !"FAILED".equals(completionStatus)) return unchanged(message);
        if (!StringUtils.hasText(message)) return unchanged(message);
        if (message.equals(ConversationLimitException.MESSAGE)) {
            return new ChatFailureAdvice(message, ConversationLimitException.CODE, NEW_CONVERSATION);
        }
        if (message.equals(TIMEOUT_MESSAGE) || message.startsWith("模型流式响应超时")) {
            return new ChatFailureAdvice(TIMEOUT_MESSAGE, "CHAT_MODEL_TIMEOUT", NEW_CONVERSATION);
        }
        if (message.contains(CONTINUE_MESSAGE)) return new ChatFailureAdvice(message, "CHAT_FAILED", NEW_CONVERSATION);
        if (message.equals("对话出错了,请稍后重试") || message.equals("对话出错了，请稍后重试")
                || message.equals("对话记忆整理暂时失败，请稍后重试，历史内容已保留")
                || message.equals("对话记忆整理暂时失败，请稍后重试，历史内容已保留。")
                || message.startsWith("模型响应中断，未收到完整结束事件")) {
            return new ChatFailureAdvice(FAILED_MESSAGE, "CHAT_FAILED", NEW_CONVERSATION);
        }
        return unchanged(message);
    }

    private static boolean isTimeout(Throwable error) {
        for (int depth = 0; error != null && depth < 16; depth++, error = error.getCause()) {
            if (error instanceof HttpTimeoutException || error instanceof SocketTimeoutException
                    || error instanceof TimeoutException) return true;
            if (error instanceof ServiceException && error.getMessage() != null
                    && error.getMessage().startsWith("模型流式响应超时")) return true;
        }
        return false;
    }

    private static ChatFailureAdvice recharge(String message) {
        return new ChatFailureAdvice(message, "INSUFFICIENT_BEANS", "RECHARGE");
    }

    private static ChatFailureAdvice unchanged(String message) {
        return new ChatFailureAdvice(message, null, null);
    }
}
