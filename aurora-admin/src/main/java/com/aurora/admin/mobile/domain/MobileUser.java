package com.aurora.admin.mobile.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 移动端用户实体
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("mobile_user")
public class MobileUser implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    @TableId(value = "user_id", type = IdType.AUTO)
    private Long userId;

    /**
     * 用户编号(Ts+yyMMddHH+4随机+4数字,全局唯一)
     */
    private String userNo;

    /**
     * 微信小程序 openid
     */
    private String wxOpenid;

    /**
     * 微信 unionid(同主体多端唯一)
     */
    private String wxUnionid;

    /**
     * 微信 session_key(AES 加密存储)。
     * 仅供服务端生成虚拟支付用户态签名,任何接口均不得序列化返回。
    */
    @JsonIgnore
    @ToString.Exclude
    private String wxSessionKey;

    /**
     * 微信 session_key 最近刷新时间
     */
    private LocalDateTime sessionKeyUpdated;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 密码(加密存储)
     */
    private String password;

    /**
     * 昵称
     */
    private String nickName;

    /**
     * 头像URL
     */
    private String avatarUrl;

    /**
     * 性别(0未知/1男/2女)
     */
    private Integer gender;

    /**
     * 来源渠道
     */
    private String source;

    /**
     * 状态(0正常/1停用)
     */
    private String status;

    /**
     * 逻辑删除标记(0存在/2删除)
     */
    @TableLogic
    private String delFlag;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 豆子余额(瞬态,非数据库字段;后台列表查询时按 user_id 关联 bean_account 填充)
     */
    @TableField(exist = false)
    private Long balance;

    /**
     * 当前会员等级名(瞬态,非数据库字段;后台列表按 user_id 取当前最高有效等级填充,非会员为 null)
     */
    @TableField(exist = false)
    private String membershipLevel;

    /**
     * 当前会员等级ID(瞬态,非数据库字段;非会员为 null)
     */
    @TableField(exist = false)
    private Long membershipLevelId;
}
