package com.aurora.ai.agent.mapper;

import com.aurora.ai.agent.domain.Message;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 对话消息明细 Mapper
 */
@Mapper
public interface MessageMapper extends BaseMapper<Message> {
}
