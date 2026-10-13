package com.aurora.ai.agent.dto;

import com.aurora.ai.agent.domain.Conversation;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话视图(PRD §十 会话列表返回项)。
 * 仅暴露 C 端所需字段,不含 userId/delFlag 等内部字段。
 */
@Data
public class ConversationVO {

    /**
     * 会话 id
     */
    private Long conversationId;

    /**
     * 标题
     */
    private String title;

    /**
     * 模型 id
     */
    private Long modelId;

    /**
     * 最近消息时间
     */
    private LocalDateTime lastMessageTime;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 由实体转视图。
     *
     * @param entity 会话实体
     * @return 视图;入参为 null 时返回 null
     */
    public static ConversationVO from(Conversation entity) {
        if (entity == null) {
            return null;
        }
        ConversationVO vo = new ConversationVO();
        vo.setConversationId(entity.getId());
        vo.setTitle(entity.getTitle());
        vo.setModelId(entity.getModelId());
        vo.setLastMessageTime(entity.getLastMessageTime());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }
}
