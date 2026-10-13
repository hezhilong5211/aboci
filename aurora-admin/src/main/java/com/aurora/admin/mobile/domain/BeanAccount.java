package com.aurora.admin.mobile.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 豆子账户实体(每个移动端用户一条,唯一索引 user_id)
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("bean_account")
public class BeanAccount implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 账户ID
     */
    @TableId(value = "account_id", type = IdType.AUTO)
    private Long accountId;

    /**
     * 用户ID(唯一)
     */
    private Long userId;

    /**
     * 当前余额(≥0)
     */
    private Long balance;

    /**
     * 累计获得豆子
     */
    private Long totalEarned;

    /**
     * 累计消耗豆子
     */
    private Long totalConsumed;

    /**
     * 累计因会员赠豆到期失效的豆子（不计入真实消费）
     */
    private Long totalExpired;

    /**
     * 累计因支付订单退款实际回收的豆子（不计入真实消费或到期）
     */
    private Long totalRevoked;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
