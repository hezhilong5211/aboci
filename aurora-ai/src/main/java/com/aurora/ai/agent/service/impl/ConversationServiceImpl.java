package com.aurora.ai.agent.service.impl;

import com.aurora.ai.agent.domain.Conversation;
import com.aurora.ai.agent.mapper.ConversationMapper;
import com.aurora.ai.agent.service.IConversationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 对话会话头 Service 实现。
 * 写方法事务边界 @Transactional(rollbackFor=Exception.class);
 * 归属校验在 getOwned 内做(非本人返回 null,供 Controller 转 403/空)。
 */
@Service
public class ConversationServiceImpl extends ServiceImpl<ConversationMapper, Conversation>
        implements IConversationService {

    private final int retentionDays;

    public ConversationServiceImpl(@Value("${ai.conversation.retention-days:30}") int retentionDays) {
        this.retentionDays = Math.max(1, retentionDays);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Conversation createConversation(Long userId, String title, Long modelId) {
        Conversation conversation = new Conversation();
        conversation.setUserId(userId);
        conversation.setTitle(StringUtils.hasText(title) ? title : "新对话");
        conversation.setModelId(modelId);
        LocalDateTime now = LocalDateTime.now();
        conversation.setLastMessageTime(now);
        conversation.setExpireTime(now.plusDays(retentionDays));
        save(conversation);
        return conversation;
    }

    @Override
    public IPage<Conversation> pageByUser(Long userId, int pageNum, int pageSize) {
        LambdaQueryWrapper<Conversation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Conversation::getUserId, userId)
                .gt(Conversation::getExpireTime, LocalDateTime.now())
                .orderByDesc(Conversation::getLastMessageTime)
                .orderByDesc(Conversation::getCreateTime);
        return page(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public Conversation getOwned(Long id, Long userId) {
        Conversation conversation = getById(id);
        if (conversation == null || !conversation.getUserId().equals(userId)
                || conversation.getExpireTime() == null
                || !conversation.getExpireTime().isAfter(LocalDateTime.now())) {
            return null;
        }
        return conversation;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void touch(Long id) {
        LocalDateTime now = LocalDateTime.now();
        Conversation conversation = new Conversation();
        conversation.setId(id);
        conversation.setLastMessageTime(now);
        conversation.setExpireTime(now.plusDays(retentionDays));
        updateById(conversation);
    }
}
