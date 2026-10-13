package com.aurora.ai.mapper;

import com.aurora.ai.agent.domain.AgentSkill;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Skill 只读 Mapper(直查物理表 skill,仅供提示词组装读取,不做写操作)。
 */
@Mapper
public interface AgentSkillMapper extends BaseMapper<AgentSkill> {
}
