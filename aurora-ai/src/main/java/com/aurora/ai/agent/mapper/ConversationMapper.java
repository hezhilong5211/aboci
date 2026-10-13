package com.aurora.ai.agent.mapper;

import com.aurora.ai.agent.domain.Conversation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

/**
 * 对话会话头 Mapper
 */
@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {

    @Select("SELECT * FROM conversation WHERE id = #{id} AND user_id = #{userId} "
            + "AND del_flag = '0' AND expire_time > NOW() FOR UPDATE")
    Conversation selectOwnedForUpdate(@Param("id") Long id, @Param("userId") Long userId);

    /** 锁定仍满足过期条件的会话，避免清理与续用并发时误删。 */
    @Select("SELECT * FROM conversation "
            + "WHERE id = #{id} AND del_flag = '0' AND expire_time <= #{cutoff} FOR UPDATE")
    Conversation selectExpiredForUpdate(@Param("id") Long id, @Param("cutoff") LocalDateTime cutoff);

    /** 保留期清理使用物理删除，避免逻辑删除行永久占用存储。 */
    @Delete("DELETE FROM conversation WHERE id = #{id}")
    int deletePermanentlyById(@Param("id") Long id);
}
