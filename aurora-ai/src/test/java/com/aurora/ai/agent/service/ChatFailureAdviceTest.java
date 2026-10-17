package com.aurora.ai.agent.service;

import com.aurora.ai.agent.billing.InsufficientBeanException;
import com.aurora.common.core.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.http.HttpTimeoutException;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

class ChatFailureAdviceTest {
    @Test
    void responseHeaderTimeoutOffersNewConversationWithoutLeakingTransportDetails() {
        var advice = ChatFailureAdvice.fromFailure(
                new CompletionException(new HttpTimeoutException("request timed out https://provider-secret")), true);
        assertEquals("CHAT_MODEL_TIMEOUT", advice.errorCode());
        assertEquals("NEW_CONVERSATION", advice.action());
        assertTrue(advice.message().contains("响应超时"));
        assertTrue(advice.message().contains("若仍超时，请稍后再试"));
        assertFalse(advice.message().contains("provider-secret"));
        assertEquals(advice, ChatFailureAdvice.restore("FAILED", "FAILED", advice.message()));
    }

    @Test
    void ordinaryFailureAndKnownServiceFailureRemainRecoverableAfterPersistence() {
        var unknown = ChatFailureAdvice.fromFailure(new IllegalStateException("internal stack details"), true);
        assertEquals("CHAT_FAILED", unknown.errorCode());
        assertFalse(unknown.message().contains("internal stack details"));
        var planned = ChatFailureAdvice.fromFailure(new ServiceException("生图计划未通过校验，本次未提交图片，请重试。"), true);
        assertTrue(planned.message().contains("本次未提交图片"));
        assertEquals(planned, ChatFailureAdvice.restore("GENERATING", "FAILED", planned.message()));
    }

    @Test
    void longProviderFailureFitsTheExistingColumnWithoutLosingRecoveryGuidance() {
        var advice = ChatFailureAdvice.fromFailure(new ServiceException("失败原因".repeat(200)), true);
        assertEquals(500, advice.message().length());
        assertTrue(advice.message().endsWith("\n" + ChatFailureAdvice.CONTINUE_MESSAGE));
        assertEquals(advice, ChatFailureAdvice.restore("FAILED", null, advice.message()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"对话出错了,请稍后重试", "对话出错了，请稍后重试",
            "对话记忆整理暂时失败，请稍后重试，历史内容已保留"})
    void legacyFailureMessagesOfferNewConversation(String message) {
        var advice = ChatFailureAdvice.restore("FAILED", null, message);
        assertEquals("CHAT_FAILED", advice.errorCode());
        assertEquals("NEW_CONVERSATION", advice.action());
    }

    @Test
    void balanceCapacityAndValidationKeepTheirDifferentActions() {
        var recharge = ChatFailureAdvice.fromFailure(new InsufficientBeanException(1, 10, 10), true);
        assertEquals("RECHARGE", recharge.action());
        assertEquals(recharge, ChatFailureAdvice.restore("GENERATING", "WAITING_RECHARGE", recharge.message()));
        var limit = ChatFailureAdvice.fromFailure(new ConversationLimitException(), false);
        assertEquals("CONVERSATION_TOO_LONG", limit.errorCode());
        assertEquals(limit, ChatFailureAdvice.restore("FAILED", null, limit.message()));
        var invalid = ChatFailureAdvice.fromFailure(new ServiceException("图片不属于当前用户"), false);
        assertNull(invalid.action());
        assertEquals(invalid, ChatFailureAdvice.restore("FAILED", null, invalid.message()));
    }

    @Test
    void cancellationAndSuccessfulRunsNeverInheritAnOldFailureAction() {
        assertNull(ChatFailureAdvice.restore("CANCELLED", "FAILED", ChatFailureAdvice.FAILED_MESSAGE).action());
        assertNull(ChatFailureAdvice.restore("GENERATING", "CANCELLED", ChatFailureAdvice.FAILED_MESSAGE).action());
        assertNull(ChatFailureAdvice.restore("COMPLETED", "COMPLETED", ChatFailureAdvice.FAILED_MESSAGE).action());
        assertNull(ChatFailureAdvice.restore("FAILED", "COMPLETED", null).action());
    }
}
