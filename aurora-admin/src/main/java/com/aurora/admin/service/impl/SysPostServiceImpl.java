package com.aurora.admin.service.impl;

import com.aurora.admin.service.SysPostService;
import com.aurora.common.core.domain.entity.SysPost;
import com.aurora.common.core.mapper.SysPostMapper;
import com.aurora.common.core.mapper.SysUserPostMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 岗位 Service 实现类
 */
@Service
public class SysPostServiceImpl extends ServiceImpl<SysPostMapper, SysPost> implements SysPostService {

    @Autowired
    private SysPostMapper postMapper;

    @Autowired
    private SysUserPostMapper userPostMapper;

    /**
     * 分页查询岗位列表
     */
    @Override
    public IPage<SysPost> selectPostPage(int pageNum, int pageSize, String postName, String status) {
        Page<SysPost> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SysPost> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(postName)) {
            wrapper.like(SysPost::getPostName, postName);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(SysPost::getStatus, status);
        }
        wrapper.orderByAsc(SysPost::getPostSort);

        return postMapper.selectPage(page, wrapper);
    }

    /**
     * 查询所有岗位列表
     */
    @Override
    public List<SysPost> selectPostAll() {
        return postMapper.selectList(
                new LambdaQueryWrapper<SysPost>()
                        .eq(SysPost::getStatus, "0")
                        .orderByAsc(SysPost::getPostSort));
    }

    /**
     * 根据岗位ID查询岗位信息
     */
    @Override
    public SysPost selectPostById(Long postId) {
        return postMapper.selectById(postId);
    }

    /**
     * 根据用户ID查询岗位ID列表
     */
    @Override
    public List<Long> selectPostIdsByUserId(Long userId) {
        return postMapper.selectPostIdsByUserId(userId);
    }

    /**
     * 新增岗位
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertPost(SysPost post) {
        post.setCreateTime(LocalDateTime.now());
        return postMapper.insert(post);
    }

    /**
     * 修改岗位
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updatePost(SysPost post) {
        post.setUpdateTime(LocalDateTime.now());
        return postMapper.updateById(post);
    }

    /**
     * 删除岗位
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deletePostById(Long postId) {
        return postMapper.deleteById(postId);
    }

    /**
     * 批量删除岗位
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deletePostByIds(List<Long> postIds) {
        return postMapper.deleteBatchIds(postIds);
    }

    /**
     * 校验岗位编码是否唯一
     */
    @Override
    public boolean checkPostCodeUnique(SysPost post) {
        Long postId = post.getPostId() == null ? -1L : post.getPostId();
        SysPost info = postMapper.selectOne(
                new LambdaQueryWrapper<SysPost>().eq(SysPost::getPostCode, post.getPostCode()));
        if (info != null && !info.getPostId().equals(postId)) {
            return false;
        }
        return true;
    }

    /**
     * 校验岗位名称是否唯一
     */
    @Override
    public boolean checkPostNameUnique(SysPost post) {
        Long postId = post.getPostId() == null ? -1L : post.getPostId();
        SysPost info = postMapper.selectOne(
                new LambdaQueryWrapper<SysPost>().eq(SysPost::getPostName, post.getPostName()));
        if (info != null && !info.getPostId().equals(postId)) {
            return false;
        }
        return true;
    }

    /**
     * 校验岗位是否被使用
     */
    @Override
    public boolean checkPostInUse(Long postId) {
        int count = userPostMapper.countUserByPostId(postId);
        return count > 0;
    }
}
