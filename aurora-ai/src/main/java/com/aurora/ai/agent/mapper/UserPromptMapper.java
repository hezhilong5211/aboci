package com.aurora.ai.agent.mapper;

import com.aurora.ai.agent.domain.UserPrompt;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户个性化提示词 Mapper
 */
@Mapper
public interface UserPromptMapper extends BaseMapper<UserPrompt> {
}
