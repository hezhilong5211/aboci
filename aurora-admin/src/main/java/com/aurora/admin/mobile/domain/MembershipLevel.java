package com.aurora.admin.mobile.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 会员等级配置实体(序号/名称/送豆数/价格 + 启停)
 * 逻辑删:仅从配置列表与可开通选项中消失,历史快照(user_membership)不受影响。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("membership_level")
public class MembershipLevel implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 等级ID
     */
    @TableId(value = "level_id", type = IdType.AUTO)
    private Long levelId;

    /**
     * 等级名称
     */
    private String name;

    /**
     * 每周期送豆数
     */
    private Long grantBeans;

    /**
     * 价格(单位:元,仅展示/记录,本期无支付)
     */
    private BigDecimal price;

    /**
     * 会员时长(天)
     */
    private Integer durationDays;

    /**
     * 微信虚拟支付普通道具ID(英文字母/数字/下划线,最长20位)
     */
    private String virtualProductId;

    /**
     * 排序序号(越大等级越高)
     */
    private Integer sort;

    /**
     * 状态(0启用/1停用)
     */
    private String status;

    /**
     * 会员卡片背景图URL(映射列 card_bg,C 端会员卡片展示用)
     */
    private String cardBg;

    /**
     * 创建者
     */
    @TableField(fill = FieldFill.INSERT)
    private String createBy;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新者
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 备注
     */
    private String remark;

    /**
     * 逻辑删除标记(0存在/2删除)
     */
    @TableLogic
    private String delFlag;
}
