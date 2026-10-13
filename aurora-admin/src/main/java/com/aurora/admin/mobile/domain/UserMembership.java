package com.aurora.admin.mobile.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户会员段实体(叠加制:一个用户可有多条有效记录,各自独立到期)
 * 开通时把当时等级信息快照写入本表;此后删改 membership_level 不影响已开通权益。
 * 「当前等级」= 用户当前有效段(status=0 且 start_time&lt;=now&lt;expire_time)
 * 中 level_sort 最高的一条；已付款但尚未到生效时间的续期段不提前算作当前会员。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("user_membership")
public class UserMembership implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 引用的等级ID(可空,配置被删后仍保留段)
     */
    private Long levelId;

    /**
     * 快照:等级序号(取最高用)
     */
    private Integer levelSort;

    /**
     * 快照:等级名称
     */
    private String levelName;

    /**
     * 快照:每周期送豆数
     */
    private Long snapshotGrantBeans;

    /**
     * 快照:价格(元)
     */
    private BigDecimal snapshotPrice;

    /**
     * 来源(MANUAL手动/PAY支付)
     */
    private String source;

    /**
     * 支付来源商户订单号(PAY 来源时填写,唯一索引保证会员发放幂等)
     */
    private String sourceOrderNo;

    /**
     * 真实支付时间(PAY 来源时填写)。
     * 该时间在会员段创建时固化，后续续费排序与退款压缩均直接读取本段快照，
     * 避免持有会员锁后再查询支付订单造成事务快照不一致或反向锁序。
     * MANUAL 来源保持为空。
     */
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime paidTime;

    /**
     * 生效时间
     */
    private LocalDateTime startTime;

    /**
     * 到期时间
     */
    private LocalDateTime expireTime;

    /**
     * 作为最后一个有效会员段到期时，豆子清零结算完成时间。
     * 仅终止会员周期的那一段填写，用于保证清零幂等且不会误清后续新获得的豆子。
     */
    private LocalDateTime expirySettledTime;

    /**
     * 状态(0有效/1过期/2取消)
     */
    private String status;

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
}
