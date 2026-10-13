package com.aurora.admin.mobile.vo;

import com.aurora.admin.mobile.domain.MobileUser;
import lombok.Data;

/**
 * 用户信息出参
 * hasPhone/hasPassword 为派生字段,供前端判断是否已绑手机号/设置密码。
 */
@Data
public class UserInfoVO {

    /** 用户ID */
    private Long userId;

    /** 用户编号(Ts+yyMMddHH+4随机+4数字,全局唯一) */
    private String userNo;

    /** 手机号 */
    private String phone;

    /** 昵称 */
    private String nickName;

    /** 头像URL */
    private String avatarUrl;

    /** 性别(0未知/1男/2女) */
    private Integer gender;

    /** 来源渠道 */
    private String source;

    /** 是否已绑定手机号 */
    private boolean hasPhone;

    /** 是否已设置密码 */
    private boolean hasPassword;

    /**
     * 由用户实体构建出参
     */
    public static UserInfoVO from(MobileUser user) {
        UserInfoVO vo = new UserInfoVO();
        vo.setUserId(user.getUserId());
        vo.setUserNo(user.getUserNo());
        vo.setPhone(user.getPhone());
        vo.setNickName(user.getNickName());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setGender(user.getGender());
        vo.setSource(user.getSource());
        vo.setHasPhone(user.getPhone() != null && !user.getPhone().isEmpty());
        vo.setHasPassword(user.getPassword() != null && !user.getPassword().isEmpty());
        return vo;
    }
}
