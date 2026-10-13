package com.aurora.ai.agent.retention;

import com.aurora.ai.agent.domain.Conversation;
import com.aurora.ai.agent.domain.Message;
import com.aurora.ai.agent.mapper.ConversationMapper;
import com.aurora.ai.agent.service.IConversationService;
import com.aurora.ai.agent.service.IMessageAttachmentService;
import com.aurora.ai.agent.service.IMessageService;
import com.aurora.ai.agent.service.IReferenceScopeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

/** 按最后使用时间清理过期会话及其全部消息。 */
@Slf4j
@Service
public class ConversationRetentionService {

    private final IConversationService conversationService;
    private final IMessageService messageService;
    private final IMessageAttachmentService messageAttachmentService;
    private final IReferenceScopeService referenceScopeService;
    private final ConversationMapper conversationMapper;
    private final TransactionTemplate transactionTemplate;
    private final int maxConversationsPerRun;

    public ConversationRetentionService(
            IConversationService conversationService,
            IMessageService messageService,
            IMessageAttachmentService messageAttachmentService,
            IReferenceScopeService referenceScopeService,
            ConversationMapper conversationMapper,
            TransactionTemplate transactionTemplate,
            @Value("${ai.conversation.cleanup.max-conversations-per-run:2000}") int maxConversationsPerRun) {
        this.conversationService = conversationService;
        this.messageService = messageService;
        this.messageAttachmentService = messageAttachmentService;
        this.referenceScopeService = referenceScopeService;
        this.conversationMapper = conversationMapper;
        this.transactionTemplate = transactionTemplate;
        this.maxConversationsPerRun = Math.max(1, maxConversationsPerRun);
    }

    /**
     * 执行一次过期清理。每个会话独立事务，单条异常不会阻断本批其他会话。
     *
     * @return 成功物理删除的会话数量
     */
    public int cleanupExpired() {
        LocalDateTime cutoff = LocalDateTime.now();
        List<Conversation> candidates = conversationService.list(new LambdaQueryWrapper<Conversation>()
                .le(Conversation::getExpireTime, cutoff)
                .orderByAsc(Conversation::getExpireTime)
                .last("LIMIT " + maxConversationsPerRun));

        int cleaned = 0;
        int skipped = 0;
        int failed = 0;
        for (Conversation candidate : candidates) {
            boolean[] removed = {false};
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    Conversation locked = conversationMapper.selectExpiredForUpdate(candidate.getId(), cutoff);
                    if (locked == null) {
                        return;
                    }
                    referenceScopeService.removeByConversation(candidate.getId());
                    messageAttachmentService.removeByConversation(candidate.getId());
                    messageService.remove(new LambdaQueryWrapper<Message>()
                            .eq(Message::getConversationId, candidate.getId()));
                    if (conversationMapper.deletePermanentlyById(candidate.getId()) != 1) {
                        throw new IllegalStateException("过期会话物理删除失败: " + candidate.getId());
                    }
                    removed[0] = true;
                });
                if (removed[0]) {
                    cleaned++;
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                failed++;
                log.error("过期会话清理失败，保留待下次重试: conversationId={}", candidate.getId(), e);
            }
        }
        if (!candidates.isEmpty()) {
            log.info("对话保留期清理完成: cutoff={}, scanned={}, cleaned={}, skipped={}, failed={}",
                    cutoff, candidates.size(), cleaned, skipped, failed);
        }
        return cleaned;
    }
}
