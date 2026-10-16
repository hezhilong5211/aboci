package com.aurora.ai.agent.mapper;

import com.aurora.ai.agent.domain.Message;
import com.aurora.ai.agent.domain.ConversationHistorySize;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 对话消息明细 Mapper
 */
@Mapper
public interface MessageMapper extends BaseMapper<Message> {
    @Select("""
            SELECT COUNT(*) AS message_count,
                   COALESCE(SUM(CHAR_LENGTH(content)), 0) AS character_count,
                   COALESCE((SELECT CHAR_LENGTH(image_index_json) FROM conversation_memory
                             WHERE conversation_id = #{conversationId} AND user_id = #{userId}), 0)
                     AS image_index_characters
            FROM message WHERE conversation_id = #{conversationId} AND user_id = #{userId}
            """)
    ConversationHistorySize historySize(@Param("userId") Long userId, @Param("conversationId") Long conversationId);
}
