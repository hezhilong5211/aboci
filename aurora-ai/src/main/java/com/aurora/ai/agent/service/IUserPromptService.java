package com.aurora.ai.agent.service;

import com.aurora.ai.agent.domain.UserPrompt;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 用户个性化提示词 Service 接口(PRD §5.4)。
 * 写入方:用户(本期仅后端 upsert);读取方:PromptAssembler。每用户一条(user_id 唯一)。
 */
public interface IUserPromptService extends IService<UserPrompt> {

    /**
     * 取本人个性化提示词。
     *
     * @param userId 当前登录用户(取自登录态)
     * @return 个性化提示词实体,无则返回 null
     */
    UserPrompt getByUserId(Long userId);

    /**
     * upsert 本人个性化提示词:有则更新,无则插入。
     * content 入库前 htmlUnescape 恢复原始提示词语义(对齐 skill/global_prompt)。
     *
     * @param userId  当前登录用户(取自登录态)
     * @param content 个性化提示词文本
     */
    void upsert(Long userId, String content);
}
