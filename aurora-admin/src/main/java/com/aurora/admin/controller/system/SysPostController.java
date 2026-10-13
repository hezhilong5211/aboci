package com.aurora.admin.controller.system;

import com.aurora.admin.service.SysPostService;
import com.aurora.common.core.domain.Result;
import com.aurora.common.core.domain.entity.SysPost;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 岗位管理 Controller
 */
@RestController
@RequestMapping("/system/post")
public class SysPostController {

    @Autowired
    private SysPostService postService;

    /**
     * 分页查询岗位列表
     */
    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermi('system:post:list')")
    public Result<IPage<SysPost>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String postName,
            @RequestParam(required = false) String status) {
        return Result.success(postService.selectPostPage(pageNum, pageSize, postName, status));
    }

    /**
     * 查询所有岗位列表（下拉选择用）
     */
    @GetMapping("/optionselect")
    @PreAuthorize("isAuthenticated()")
    public Result<List<SysPost>> optionSelect() {
        return Result.success(postService.selectPostAll());
    }

    /**
     * 根据岗位ID获取岗位信息
     */
    @GetMapping("/{postId}")
    @PreAuthorize("@ss.hasPermi('system:post:query')")
    public Result<SysPost> getInfo(@PathVariable Long postId) {
        return Result.success(postService.selectPostById(postId));
    }

    /**
     * 新增岗位
     */
    @PostMapping
    @PreAuthorize("@ss.hasPermi('system:post:add')")
    public Result<Void> add(@RequestBody SysPost post) {
        if (!postService.checkPostCodeUnique(post)) {
            return Result.error("新增岗位'" + post.getPostName() + "'失败，岗位编码已存在");
        }

        if (!postService.checkPostNameUnique(post)) {
            return Result.error("新增岗位'" + post.getPostName() + "'失败，岗位名称已存在");
        }

        int rows = postService.insertPost(post);
        return rows > 0 ? Result.success() : Result.error("新增失败");
    }

    /**
     * 修改岗位
     */
    @PutMapping
    @PreAuthorize("@ss.hasPermi('system:post:edit')")
    public Result<Void> edit(@RequestBody SysPost post) {
        if (!postService.checkPostCodeUnique(post)) {
            return Result.error("修改岗位'" + post.getPostName() + "'失败，岗位编码已存在");
        }

        if (!postService.checkPostNameUnique(post)) {
            return Result.error("修改岗位'" + post.getPostName() + "'失败，岗位名称已存在");
        }

        int rows = postService.updatePost(post);
        return rows > 0 ? Result.success() : Result.error("修改失败");
    }

    /**
     * 删除岗位
     */
    @DeleteMapping("/{postId}")
    @PreAuthorize("@ss.hasPermi('system:post:remove')")
    public Result<Void> remove(@PathVariable Long postId) {
        if (postService.checkPostInUse(postId)) {
            return Result.error("岗位已分配给用户，不允许删除");
        }

        int rows = postService.deletePostById(postId);
        return rows > 0 ? Result.success() : Result.error("删除失败");
    }

    /**
     * 批量删除岗位
     */
    @DeleteMapping("/batch")
    @PreAuthorize("@ss.hasPermi('system:post:remove')")
    public Result<Void> batchRemove(@RequestBody List<Long> postIds) {
        // 检查是否有岗位被使用
        for (Long postId : postIds) {
            if (postService.checkPostInUse(postId)) {
                SysPost post = postService.selectPostById(postId);
                return Result.error("岗位'" + (post != null ? post.getPostName() : postId) + "'已分配给用户，不允许删除");
            }
        }

        int rows = postService.deletePostByIds(postIds);
        return rows > 0 ? Result.success() : Result.error("删除失败");
    }
}
