package com.aurora.ai.agent.service.impl;

import com.aurora.ai.agent.domain.ReferenceScope;
import com.aurora.ai.agent.dto.ClaimedReferenceScope;
import com.aurora.ai.agent.mapper.ReferenceScopeMapper;
import com.aurora.ai.agent.service.IReferenceScopeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Agent 一次性澄清图片引用范围 Service 实现。 */
@Service
public class ReferenceScopeServiceImpl extends ServiceImpl<ReferenceScopeMapper, ReferenceScope>
        implements IReferenceScopeService {

    private final ObjectMapper objectMapper;

    public ReferenceScopeServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReferenceScope open(Long conversationId, Long userId, Long sourceMessageId,
                               List<String> attachmentIds, String question, boolean allowAdditionalImages,
                               String contextJson, int roundNo, LocalDateTime expiresAt) {
        cancelOpen(conversationId, userId);
        ReferenceScope scope = new ReferenceScope();
        scope.setScopeId(UUID.randomUUID().toString().replace("-", ""));
        scope.setConversationId(conversationId);
        scope.setUserId(userId);
        scope.setSourceMessageId(sourceMessageId);
        scope.setAttachmentIds(writeAttachmentIds(attachmentIds));
        scope.setQuestion(question);
        scope.setAllowAdditionalImages(allowAdditionalImages ? 1 : 0);
        scope.setContextJson(contextJson);
        scope.setRoundNo(Math.max(1, roundNo));
        scope.setExpiresAt(expiresAt);
        scope.setStatus("OPEN");
        LocalDateTime now = LocalDateTime.now();
        scope.setCreateTime(now);
        scope.setUpdateTime(now);
        save(scope);
        return scope;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ClaimedReferenceScope claim(String scopeId, Long conversationId, Long userId,
                                       Long consumingMessageId) {
        if (scopeId == null || scopeId.isBlank()) {
            return null;
        }
        ReferenceScope scope = getOne(new LambdaQueryWrapper<ReferenceScope>()
                .eq(ReferenceScope::getScopeId, scopeId)
                .eq(ReferenceScope::getConversationId, conversationId)
                .eq(ReferenceScope::getUserId, userId)
                .eq(ReferenceScope::getStatus, "OPEN")
                .last("LIMIT 1"));
        return consume(scope, conversationId, userId, consumingMessageId);
    }

    private ClaimedReferenceScope consume(ReferenceScope scope, Long conversationId, Long userId,
                                          Long consumingMessageId) {
        if (scope == null) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        if (scope.getExpiresAt() != null && !scope.getExpiresAt().isAfter(now)) {
            update(new LambdaUpdateWrapper<ReferenceScope>()
                    .eq(ReferenceScope::getId, scope.getId())
                    .eq(ReferenceScope::getStatus, "OPEN")
                    .set(ReferenceScope::getStatus, "CANCELLED")
                    .set(ReferenceScope::getUpdateTime, now));
            return null;
        }
        boolean claimed = update(new LambdaUpdateWrapper<ReferenceScope>()
                .eq(ReferenceScope::getId, scope.getId())
                .eq(ReferenceScope::getStatus, "OPEN")
                .set(ReferenceScope::getStatus, "CONSUMED")
                .set(ReferenceScope::getConsumedByMessageId, consumingMessageId)
                .set(ReferenceScope::getUpdateTime, now));
        if (!claimed) {
            return null;
        }
        // 并发对话极端情况下可能同时创建多条 OPEN；消费最新一条后立即作废其余范围，防止旧图在后续轮次复活。
        update(new LambdaUpdateWrapper<ReferenceScope>()
                .eq(ReferenceScope::getConversationId, conversationId)
                .eq(ReferenceScope::getUserId, userId)
                .eq(ReferenceScope::getStatus, "OPEN")
                .ne(ReferenceScope::getId, scope.getId())
                .set(ReferenceScope::getStatus, "CANCELLED")
                .set(ReferenceScope::getUpdateTime, LocalDateTime.now()));
        return new ClaimedReferenceScope(scope, readAttachmentIds(scope.getAttachmentIds()));
    }

    @Override
    public ReferenceScope getOpen(Long conversationId, Long userId) {
        ReferenceScope scope = getOne(new LambdaQueryWrapper<ReferenceScope>()
                .eq(ReferenceScope::getConversationId, conversationId)
                .eq(ReferenceScope::getUserId, userId)
                .eq(ReferenceScope::getStatus, "OPEN")
                .orderByDesc(ReferenceScope::getId)
                .last("LIMIT 1"));
        if (scope != null && scope.getExpiresAt() != null
                && !scope.getExpiresAt().isAfter(LocalDateTime.now())) {
            cancel(scope.getScopeId());
            return null;
        }
        return scope;
    }

    @Override
    public void cancelOpen(Long conversationId, Long userId) {
        update(new LambdaUpdateWrapper<ReferenceScope>()
                .eq(ReferenceScope::getConversationId, conversationId)
                .eq(ReferenceScope::getUserId, userId)
                .eq(ReferenceScope::getStatus, "OPEN")
                .set(ReferenceScope::getStatus, "CANCELLED")
                .set(ReferenceScope::getUpdateTime, LocalDateTime.now()));
    }

    @Override
    public void cancel(String scopeId) {
        if (scopeId == null || scopeId.isBlank()) {
            return;
        }
        update(new LambdaUpdateWrapper<ReferenceScope>()
                .eq(ReferenceScope::getScopeId, scopeId)
                .eq(ReferenceScope::getStatus, "OPEN")
                .set(ReferenceScope::getStatus, "CANCELLED")
                .set(ReferenceScope::getUpdateTime, LocalDateTime.now()));
    }

    @Override
    public void cancelByAttachmentIds(List<String> attachmentIds) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return;
        }
        LambdaUpdateWrapper<ReferenceScope> update = new LambdaUpdateWrapper<ReferenceScope>()
                .eq(ReferenceScope::getStatus, "OPEN")
                .and(wrapper -> {
                    for (int i = 0; i < attachmentIds.size(); i++) {
                        String value = "\"" + attachmentIds.get(i) + "\"";
                        if (i == 0) {
                            wrapper.like(ReferenceScope::getAttachmentIds, value);
                        } else {
                            wrapper.or().like(ReferenceScope::getAttachmentIds, value);
                        }
                    }
                })
                .set(ReferenceScope::getStatus, "CANCELLED")
                .set(ReferenceScope::getUpdateTime, LocalDateTime.now());
        update(update);
    }

    @Override
    public void removeByConversation(Long conversationId) {
        remove(new LambdaQueryWrapper<ReferenceScope>()
                .eq(ReferenceScope::getConversationId, conversationId));
    }

    private String writeAttachmentIds(List<String> attachmentIds) {
        try {
            return objectMapper.writeValueAsString(attachmentIds == null ? List.of() : attachmentIds);
        } catch (Exception e) {
            throw new IllegalArgumentException("附件引用序列化失败", e);
        }
    }

    private List<String> readAttachmentIds(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() { });
        } catch (Exception e) {
            throw new IllegalStateException("附件引用解析失败", e);
        }
    }
}
