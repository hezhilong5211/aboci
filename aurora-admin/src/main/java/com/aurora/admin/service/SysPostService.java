package com.aurora.admin.service;

import com.aurora.common.core.domain.entity.SysPost;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 岗位 Service 接口
 */
public interface SysPostService extends IService<SysPost> {

    /**
     * 分页查询岗位列表
     */
    IPage<SysPost> selectPostPage(int pageNum, int pageSize, String postName, String status);

    /**
     * 查询所有岗位列表
     */
    List<SysPost> selectPostAll();

    /**
     * 根据岗位ID查询岗位信息
     */
    SysPost selectPostById(Long postId);

    /**
     * 根据用户ID查询岗位ID列表
     */
    List<Long> selectPostIdsByUserId(Long userId);

    /**
     * 新增岗位
     */
    int insertPost(SysPost post);

    /**
     * 修改岗位
     */
    int updatePost(SysPost post);

    /**
     * 删除岗位
     */
    int deletePostById(Long postId);

    /**
     * 批量删除岗位
     */
    int deletePostByIds(List<Long> postIds);

    /**
     * 校验岗位编码是否唯一
     */
    boolean checkPostCodeUnique(SysPost post);

    /**
     * 校验岗位名称是否唯一
     */
    boolean checkPostNameUnique(SysPost post);

    /**
     * 校验岗位是否被使用
     */
    boolean checkPostInUse(Long postId);
}
