package com.aurora.common.core.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 用户与岗位关联对象 sys_user_post
 */
@Data
@NoArgsConstructor
@TableName("sys_user_post")
public class SysUserPost implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 用户ID */
    private Long userId;

    /** 岗位ID */
    private Long postId;

    public SysUserPost(Long userId, Long postId) {
        this.userId = userId;
        this.postId = postId;
    }
}
