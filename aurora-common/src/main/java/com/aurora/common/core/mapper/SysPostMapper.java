package com.aurora.common.core.mapper;

import com.aurora.common.core.domain.entity.SysPost;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 岗位 Mapper 接口
 */
@Mapper
public interface SysPostMapper extends BaseMapper<SysPost> {

    /**
     * 根据用户ID查询岗位ID列表
     */
    @Select("SELECT post_id FROM sys_user_post WHERE user_id = #{userId}")
    List<Long> selectPostIdsByUserId(@Param("userId") Long userId);

    /**
     * 检查岗位编码是否唯一
     */
    @Select("SELECT COUNT(1) FROM sys_post WHERE post_code = #{postCode}")
    int checkPostCodeUnique(@Param("postCode") String postCode);

    /**
     * 检查岗位名称是否唯一
     */
    @Select("SELECT COUNT(1) FROM sys_post WHERE post_name = #{postName}")
    int checkPostNameUnique(@Param("postName") String postName);
}
