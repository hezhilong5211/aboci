package com.aurora.ai.agent.service.impl;

import com.aurora.ai.agent.domain.UserPrompt;
import com.aurora.ai.agent.mapper.UserPromptMapper;
import com.aurora.ai.agent.service.IUserPromptService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

/**
 * 用户个性化提示词 Service 实现(PRD §5.4)。
 * 每用户一条(user_id 唯一);content 入库前 htmlUnescape 恢复原始提示词语义(对齐 skill/global_prompt)。
 */
@Service
public class UserPromptServiceImpl extends ServiceImpl<UserPromptMapper, UserPrompt>
        implements IUserPromptService {

    @Override
    public UserPrompt getByUserId(Long userId) {
        return getOne(new LambdaQueryWrapper<UserPrompt>().eq(UserPrompt::getUserId, userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void upsert(Long userId, String content) {
        // content 是送大模型原始文本,XSS 过滤器会转义 "<>& 污染提示词语义,入库前反转义恢复原文
        String original = content == null ? null : HtmlUtils.htmlUnescape(content);
        UserPrompt exist = getByUserId(userId);
        if (exist == null) {
            UserPrompt entity = new UserPrompt();
            entity.setUserId(userId);
            entity.setContent(original);
            save(entity);
        } else {
            exist.setContent(original);
            updateById(exist);
        }
    }
}
