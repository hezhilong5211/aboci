package com.aurora.common.core.mapper;

import com.aurora.common.core.domain.entity.SysUserPost;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户与岗位关联 Mapper 接口
 */
@Mapper
public interface SysUserPostMapper extends BaseMapper<SysUserPost> {

    /**
     * 根据用户ID删除用户和岗位关联
     */
    @Delete("DELETE FROM sys_user_post WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);

    /**
     * 根据岗位ID查询使用该岗位的用户数量
     */
    @Select("SELECT COUNT(1) FROM sys_user_post WHERE post_id = #{postId}")
    int countUserByPostId(@Param("postId") Long postId);
}
