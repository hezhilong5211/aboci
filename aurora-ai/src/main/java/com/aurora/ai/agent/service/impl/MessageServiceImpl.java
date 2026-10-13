package com.aurora.ai.agent.service.impl;

import com.aurora.ai.agent.domain.Message;
import com.aurora.ai.agent.domain.MessageAttachment;
import com.aurora.ai.agent.dto.MessageAttachmentDraft;
import com.aurora.ai.agent.mapper.MessageAttachmentMapper;
import com.aurora.ai.agent.mapper.MessageMapper;
import com.aurora.ai.agent.service.IMessageService;
import com.aurora.common.core.exception.ServiceException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 对话消息明细 Service 实现。
 * message 为轻量流水(无逻辑删),写方法事务边界 @Transactional(rollbackFor=Exception.class);
 * create_time 由 Service 显式设置(实体不走 @TableField 自动填充),保证排序与上下文回灌有稳定时序。
 */
@Service
public class MessageServiceImpl extends ServiceImpl<MessageMapper, Message> implements IMessageService {

    private final MessageAttachmentMapper messageAttachmentMapper;

    public MessageServiceImpl(MessageAttachmentMapper messageAttachmentMapper) {
        this.messageAttachmentMapper = messageAttachmentMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Message saveMessage(Long conversationId, Long userId, String role, String content,
                               String contentType, String media, String toolName, String toolCallId) {
        Message message = new Message();
        message.setConversationId(conversationId);
        message.setUserId(userId);
        message.setRole(role);
        message.setContent(content);
        message.setContentType(StringUtils.hasText(contentType) ? contentType : "text");
        message.setMedia(media);
        message.setToolName(toolName);
        message.setToolCallId(toolCallId);
        message.setCreateTime(LocalDateTime.now());
        save(message);
        return message;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Message saveUserMessage(Long conversationId, Long userId, String content, String media,
                                   List<MessageAttachmentDraft> attachments) {
        Message message = saveMessage(conversationId, userId, "user", content,
                "text", media, null, null);
        LocalDateTime now = LocalDateTime.now();
        if (attachments != null) {
            for (MessageAttachmentDraft draft : attachments) {
                MessageAttachment attachment = new MessageAttachment();
                attachment.setAttachmentId(draft.attachmentId());
                attachment.setMessageId(message.getId());
                attachment.setConversationId(conversationId);
                attachment.setUserId(userId);
                attachment.setPosition(draft.position());
                attachment.setSourceType(draft.sourceType());
                attachment.setProfileUrl(draft.profileUrl());
                attachment.setSourceTaskId(draft.sourceTaskId());
                attachment.setSourceSeq(draft.sourceSeq());
                attachment.setCreateTime(now);
                messageAttachmentMapper.insert(attachment);
            }
        }
        return message;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void appendUserAttachments(Long userId, Long messageId, String media, List<MessageAttachmentDraft> attachments) {
        Message message = getById(messageId);
        if (message == null || !userId.equals(message.getUserId()) || !"user".equals(message.getRole())) {
            throw new ServiceException("消息不存在或无权访问");
        }
        for (MessageAttachmentDraft draft : attachments) {
            MessageAttachment attachment = new MessageAttachment();
            attachment.setAttachmentId(draft.attachmentId());
            attachment.setMessageId(messageId);
            attachment.setConversationId(message.getConversationId());
            attachment.setUserId(userId);
            attachment.setPosition(draft.position());
            attachment.setSourceType(draft.sourceType());
            attachment.setProfileUrl(draft.profileUrl());
            attachment.setSourceTaskId(draft.sourceTaskId());
            attachment.setSourceSeq(draft.sourceSeq());
            attachment.setCreateTime(LocalDateTime.now());
            messageAttachmentMapper.insert(attachment);
        }
        message.setMedia(media);
        updateById(message);
    }

    @Override
    public List<Message> listByConversation(Long conversationId) {
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Message::getConversationId, conversationId)
                .orderByAsc(Message::getCreateTime)
                .orderByAsc(Message::getId);
        return list(wrapper);
    }

    @Override
    public List<Message> listBefore(Long conversationId, Long beforeId, int limit) {
        if (conversationId == null || limit <= 0) {
            return List.of();
        }
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Message::getConversationId, conversationId)
                .lt(beforeId != null, Message::getId, beforeId)
                .orderByDesc(Message::getId);
        List<Message> descending = page(new Page<>(1, limit, false), wrapper).getRecords();
        List<Message> ordered = new ArrayList<>(descending);
        Collections.reverse(ordered);
        return ordered;
    }

    @Override
    public List<Message> recentForContext(Long conversationId, int n) {
        // 先按时间倒序取最近 n 条,再在内存反转为正序回灌
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Message::getConversationId, conversationId)
                .orderByDesc(Message::getCreateTime)
                .orderByDesc(Message::getId);
        List<Message> recent = page(new Page<>(1, n), wrapper).getRecords();
        List<Message> ordered = new ArrayList<>(recent);
        Collections.reverse(ordered);
        return ordered;
    }
}
