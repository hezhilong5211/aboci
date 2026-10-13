package com.aurora.ai.agent.service;

import com.aurora.ai.agent.domain.ReferenceScope;
import com.aurora.ai.agent.dto.ClaimedReferenceScope;
import com.baomidou.mybatisplus.extension.service.IService;

import java.time.LocalDateTime;
import java.util.List;

/** Agent 一次性澄清图片引用范围 Service。 */
public interface IReferenceScopeService extends IService<ReferenceScope> {

    ReferenceScope open(Long conversationId, Long userId, Long sourceMessageId,
                        List<String> attachmentIds, String question, boolean allowAdditionalImages,
                        String contextJson, int roundNo, LocalDateTime expiresAt);

    /** 只消费客户端明确回传的范围，禁止把任意下一条消息当作澄清回答。 */
    ClaimedReferenceScope claim(String scopeId, Long conversationId, Long userId, Long consumingMessageId);

    ReferenceScope getOpen(Long conversationId, Long userId);

    void cancelOpen(Long conversationId, Long userId);

    void cancel(String scopeId);

    void cancelByAttachmentIds(List<String> attachmentIds);

    void removeByConversation(Long conversationId);
}
