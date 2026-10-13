package com.aurora.ai.mapper;

import com.aurora.ai.agent.domain.AgentGlobalPrompt;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 全局提示词只读 Mapper(直查物理表 global_prompt,仅供提示词组装读取,不做写操作)。
 */
@Mapper
public interface AgentGlobalPromptMapper extends BaseMapper<AgentGlobalPrompt> {
}
