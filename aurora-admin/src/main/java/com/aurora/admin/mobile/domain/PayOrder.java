package com.aurora.admin.mobile.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 支付订单实体
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("pay_order")
public class PayOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 商户订单号
     */
    private String orderNo;

    /**
     * 下单用户ID
     */
    private Long userId;

    /**
     * 支付用户 openid
     */
    private String openId;

    /**
     * 金额(单位:分)
     */
    private Integer amount;

    /**
     * 商品描述
     */
    private String subject;

    /**
     * 订单类型(MEMBERSHIP会员/BEAN_RECHARGE豆子充值)
     */
    private String productType;

    /**
     * 关联业务id(会员等级id/套餐id)
     */
    private Long productId;

    /**
     * 商品名称快照
     */
    private String productName;

    /**
     * 支付渠道(WXPAY传统微信支付/VIRTUAL虚拟支付)
     */
    private String payChannel;

    /**
     * 微信虚拟支付订单号
     */
    private String wxOrderId;

    /**
     * 虚拟支付环境(0现网/1沙箱)
     */
    private Integer env;

    /**
     * 微信普通道具ID快照
     */
    private String virtualProductId;

    /**
     * 豆子套餐到账豆数快照
     */
    private Long snapshotBeans;

    /**
     * 会员有效天数快照
     */
    private Integer snapshotDurationDays;

    /**
     * 会员赠豆数快照
     */
    private Long snapshotGrantBeans;

    /**
     * 会员等级排序快照
     */
    private Integer snapshotLevelSort;

    /**
     * 履约状态(0待发放/1已发放/2失败)
     */
    private Integer fulfillmentStatus;

    /**
     * 最近履约失败原因
     */
    private String fulfillmentError;

    /**
     * 履约完成时间
     */
    private LocalDateTime fulfilledAt;

    /**
     * 微信侧是否已确认发货(回调成功应答或 notify_provide_goods 成功)
     */
    private Integer deliveryConfirmed;

    /**
     * 微信侧发货确认时间
     */
    private LocalDateTime deliveryConfirmedAt;

    /**
     * 状态(0待支付/1已支付/2已退款/3已关闭/4已支付待退款)
     */
    private Integer status;

    /**
     * 微信支付交易单号
     */
    private String transactionId;

    /**
     * 支付完成时间
     */
    private LocalDateTime payTime;

    /**
     * 退款单号
     */
    private String refundNo;

    /**
     * 退款金额(单位:分)
     */
    private Integer refundAmount;

    /**
     * 退款时间
     */
    private LocalDateTime refundTime;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
