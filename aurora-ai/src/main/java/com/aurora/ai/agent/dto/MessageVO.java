package com.aurora.ai.agent.dto;

import com.aurora.ai.agent.domain.Message;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息视图(PRD §十 历史消息返回项)。
 * media 原样透出 JSON 串(images 的 taskId→url 解析由编排层在拼装响应时处理,本视图不内联解析,保持轻量)。
 */
@Data
public class MessageVO {

    /**
     * 消息 id
     */
    private Long id;

    /**
     * 角色(user / assistant / tool)
     */
    private String role;

    /**
     * 内容(文本或结构化 JSON 串)
     */
    private String content;

    /**
     * 内容类型(text / structured / clarification)
     */
    private String contentType;

    /**
     * 媒体引用 JSON 串(上传图 URL 列表或 [{taskId,seq}])
     */
    private String media;

    /**
     * role=tool 时的工具名
     */
    private String toolName;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 由实体转视图。
     *
     * @param entity 消息实体
     * @return 视图;入参为 null 时返回 null
     */
    public static MessageVO from(Message entity) {
        if (entity == null) {
            return null;
        }
        MessageVO vo = new MessageVO();
        vo.setId(entity.getId());
        vo.setRole(entity.getRole());
        vo.setContent(entity.getContent());
        vo.setContentType(entity.getContentType());
        vo.setMedia(entity.getMedia());
        vo.setToolName(entity.getToolName());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }
}
