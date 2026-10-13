package com.aurora.admin.mobile.dto;

import lombok.Data;

/**
 * 更新用户信息入参(白名单字段:昵称/头像/性别)
 */
@Data
public class UpdateUserDTO {

    /** 昵称 */
    private String nickName;

    /** 头像URL */
    private String avatarUrl;

    /** 性别(0未知/1男/2女) */
    private Integer gender;
}
