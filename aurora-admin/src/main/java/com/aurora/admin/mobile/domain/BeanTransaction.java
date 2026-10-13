package com.aurora.admin.mobile.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 豆子流水实体(账户每次变动记一条,balance_after 为变动后余额快照)
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("bean_transaction")
public class BeanTransaction implements Serializable {

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
     * 类型(GIFT赠送/ISSUE发放/RECHARGE充值/DEDUCT扣费/REFUND退还/ADJUST后台调整/
     * EXPIRE会员赠豆到期失效/REVOKE支付退款权益回收/RENEW_RESTORE连续续费恢复)
     */
    private String type;

    /**
     * 变动值(正负)
     */
    private Long changeValue;

    /**
     * 变动后余额快照
     */
    private Long balanceAfter;

    /**
     * 关联业务单号(可空)
     */
    private String bizNo;

    /**
     * 来源业务单号（RENEW_RESTORE 时为旧会员段的 EXPIRE 流水 biz_no；
     * 旧任务晚到退豆的 EXPIRE 时为“会员到期单|任务单”）
     */
    private String sourceBizNo;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
