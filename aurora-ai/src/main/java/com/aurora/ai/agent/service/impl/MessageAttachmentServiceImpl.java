package com.aurora.ai.agent.service.impl;

import com.aurora.ai.agent.domain.MessageAttachment;
import com.aurora.ai.agent.mapper.MessageAttachmentMapper;
import com.aurora.ai.agent.service.IMessageAttachmentService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 用户消息图片附件 Service 实现。 */
@Service
public class MessageAttachmentServiceImpl extends ServiceImpl<MessageAttachmentMapper, MessageAttachment>
        implements IMessageAttachmentService {

    @Override
    public List<MessageAttachment> listByMessage(Long messageId) {
        return list(new LambdaQueryWrapper<MessageAttachment>()
                .eq(MessageAttachment::getMessageId, messageId)
                .orderByAsc(MessageAttachment::getPosition));
    }

    @Override
    public List<MessageAttachment> listOwnedOrdered(Long conversationId, Long userId,
                                                    List<String> attachmentIds) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return List.of();
        }
        List<MessageAttachment> rows = list(new LambdaQueryWrapper<MessageAttachment>()
                .eq(MessageAttachment::getConversationId, conversationId)
                .eq(MessageAttachment::getUserId, userId)
                .in(MessageAttachment::getAttachmentId, attachmentIds));
        Map<String, MessageAttachment> byId = new LinkedHashMap<>();
        for (MessageAttachment row : rows) {
            byId.put(row.getAttachmentId(), row);
        }
        List<MessageAttachment> ordered = new ArrayList<>(attachmentIds.size());
        for (String attachmentId : attachmentIds) {
            MessageAttachment row = byId.get(attachmentId);
            if (row != null) {
                ordered.add(row);
            }
        }
        return ordered;
    }

    @Override
    public List<MessageAttachment> listBySourceTask(String taskId) {
        return list(new LambdaQueryWrapper<MessageAttachment>()
                .eq(MessageAttachment::getSourceTaskId, taskId));
    }

    @Override
    public void removeByConversation(Long conversationId) {
        remove(new LambdaQueryWrapper<MessageAttachment>()
                .eq(MessageAttachment::getConversationId, conversationId));
    }

    @Override
    public void removeBySourceTask(String taskId) {
        remove(new LambdaQueryWrapper<MessageAttachment>()
                .eq(MessageAttachment::getSourceTaskId, taskId));
    }
}
