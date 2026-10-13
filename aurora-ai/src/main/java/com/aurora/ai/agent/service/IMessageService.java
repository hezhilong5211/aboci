package com.aurora.ai.agent.service;

import com.aurora.ai.agent.domain.Message;
import com.aurora.ai.agent.dto.MessageAttachmentDraft;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 对话消息明细 Service 接口
 */
public interface IMessageService extends IService<Message> {

    /**
     * 保存一条消息(自动填 create_time)。
     *
     * @param conversationId 所属会话
     * @param userId         所属用户(冗余,登录态)
     * @param role           角色(user / assistant / tool)
     * @param content        内容(文本或结构化 JSON 串)
     * @param contentType    内容类型(text / structured / clarification),为空默认 text
     * @param media          有序媒体引用 JSON 串(可空)
     * @param toolName       工具名(role=tool 时,可空)
     * @param toolCallId     工具调用关联 id(可空)
     * @return 已落库的消息(含生成的 id)
     */
    Message saveMessage(Long conversationId, Long userId, String role, String content,
                        String contentType, String media, String toolName, String toolCallId);

    /** 保存用户消息及其有序图片附件，两者同事务提交。 */
    Message saveUserMessage(Long conversationId, Long userId, String content, String media,
                            List<MessageAttachmentDraft> attachments);

    /**
     * 查询会话全部消息,按 create_time、id 正序。
     *
     * @param conversationId 所属会话
     * @return 消息列表
     */
    List<Message> listByConversation(Long conversationId);

    /**
     * 按消息编号向前读取一段历史，结果按消息编号正序返回。
     *
     * @param conversationId 所属会话
     * @param beforeId       只读取小于该编号的消息；为空时从最新消息开始
     * @param limit          最大返回数量
     * @return 当前历史片段，按 id 正序
     */
    List<Message> listBefore(Long conversationId, Long beforeId, int limit);

    /**
     * 取会话最近 n 条消息,按 create_time、id 正序返回(供跨轮上下文回灌)。
     *
     * @param conversationId 所属会话
     * @param n              条数
     * @return 最近 n 条,正序
     */
    List<Message> recentForContext(Long conversationId, int n);
}
