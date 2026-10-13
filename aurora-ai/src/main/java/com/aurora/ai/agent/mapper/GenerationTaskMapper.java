package com.aurora.ai.agent.mapper;

import com.aurora.ai.agent.domain.GenerationTask;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 出图异步任务 Mapper
 */
@Mapper
public interface GenerationTaskMapper extends BaseMapper<GenerationTask> {
}
