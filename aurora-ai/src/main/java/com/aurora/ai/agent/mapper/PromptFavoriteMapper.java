package com.aurora.ai.agent.mapper;

import com.aurora.ai.agent.domain.PromptFavorite;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 用户提示词收藏 Mapper。
 */
@Mapper
public interface PromptFavoriteMapper extends BaseMapper<PromptFavorite> {

    /**
     * 查询包含逻辑删除记录的同内容收藏。
     */
    @Select("""
            SELECT id, user_id, name, content, content_hash, create_by, create_time,
                   update_by, update_time, remark, del_flag
              FROM prompt_favorite
             WHERE user_id = #{userId}
               AND content_hash = #{contentHash}
             LIMIT 1
            """)
    PromptFavorite selectAnyByUserAndHash(@Param("userId") Long userId,
                                          @Param("contentHash") String contentHash);

    /**
     * 恢复本人已删除的同内容收藏。
     */
    @Update("""
            UPDATE prompt_favorite
               SET name = #{name},
                   content = #{content},
                   del_flag = '0',
                   update_time = NOW()
             WHERE id = #{id}
               AND user_id = #{userId}
               AND del_flag = '2'
            """)
    int restore(@Param("id") Long id,
                @Param("userId") Long userId,
                @Param("name") String name,
                @Param("content") String content);
}
