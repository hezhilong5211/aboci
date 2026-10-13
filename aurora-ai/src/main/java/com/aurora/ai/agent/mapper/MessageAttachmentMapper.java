package com.aurora.ai.agent.mapper;

import com.aurora.ai.agent.domain.MessageAttachment;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** 消息图片附件 Mapper。 */
@Mapper
public interface MessageAttachmentMapper extends BaseMapper<MessageAttachment> {
}
