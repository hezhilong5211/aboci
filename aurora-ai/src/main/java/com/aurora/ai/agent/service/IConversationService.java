package com.aurora.ai.agent.service;

import com.aurora.ai.agent.domain.Conversation;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 对话会话头 Service 接口
 */
public interface IConversationService extends IService<Conversation> {

    /**
     * 新建会话。userId 由调用方从登录态传入;expire_time 设为 30 天后;title 为空兜底「新对话」。
     *
     * @param userId  所属用户(登录态)
     * @param title   标题(可空)
     * @param modelId 模型 id(可空)
     * @return 已落库的会话(含生成的 id)
     */
    Conversation createConversation(Long userId, String title, Long modelId);

    /**
     * 分页查询本人会话列表,按 last_message_time 倒序(空值兜底按 create_time)。
     *
     * @param userId   所属用户(登录态)
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    IPage<Conversation> pageByUser(Long userId, int pageNum, int pageSize);

    /**
     * 取本人拥有的会话;非本人或不存在返回 null(归属校验,防越权)。
     *
     * @param id     会话 id
     * @param userId 所属用户(登录态)
     * @return 会话;非本人返回 null
     */
    Conversation getOwned(Long id, Long userId);

    /**
     * 更新会话的 last_message_time，并将 expire_time 从本次使用时间起顺延保留期。
     *
     * @param id 会话 id
     */
    void touch(Long id);
}
