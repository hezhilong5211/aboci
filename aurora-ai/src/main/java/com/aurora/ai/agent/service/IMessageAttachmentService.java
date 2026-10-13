package com.aurora.ai.agent.service;

import com.aurora.ai.agent.domain.MessageAttachment;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/** 用户消息图片附件 Service。 */
public interface IMessageAttachmentService extends IService<MessageAttachment> {

    List<MessageAttachment> listByMessage(Long messageId);

    List<MessageAttachment> listOwnedOrdered(Long conversationId, Long userId, List<String> attachmentIds);

    List<MessageAttachment> listBySourceTask(String taskId);

    void removeByConversation(Long conversationId);

    void removeBySourceTask(String taskId);
}
