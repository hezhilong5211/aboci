package com.aurora.admin.mobile.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会员列表行视图:移动端用户基础信息 + 当前等级(有效段中 level_sort 最高一条)。
 * 当前等级取自 user_membership 快照,不依赖 membership_level 现值(快照兜底)。
 */
@Data
public class MembershipUserVO {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户编号
     */
    private String userNo;

    /**
     * 昵称
     */
    private String nickName;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 头像URL
     */
    private String avatarUrl;

    /**
     * 当前等级ID(有效段中 level_sort 最高一条的引用等级ID,可空)
     */
    private Long currentLevelId;

    /**
     * 当前等级名(有效段中 level_sort 最高一条的快照等级名)
     */
    private String currentLevelName;

    /**
     * 当前等级到期时间(有效段中 level_sort 最高一条的到期时间)
     */
    private LocalDateTime currentExpireTime;
}
