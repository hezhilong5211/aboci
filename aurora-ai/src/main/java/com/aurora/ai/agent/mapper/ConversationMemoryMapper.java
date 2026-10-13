package com.aurora.ai.agent.mapper;

import com.aurora.ai.agent.domain.ConversationMemory;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConversationMemoryMapper extends BaseMapper<ConversationMemory> {
}
