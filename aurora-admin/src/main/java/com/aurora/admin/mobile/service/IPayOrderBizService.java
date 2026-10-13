package com.aurora.admin.mobile.service;

import com.aurora.admin.mobile.vo.CreateOrderVO;
import com.aurora.admin.mobile.vo.OrderVO;
import com.aurora.admin.mobile.vo.PurchaseRecordVO;
import com.baomidou.mybatisplus.core.metadata.IPage;

import java.time.LocalDateTime;

/**
 * 微信支付业务 Service 接口
 * 负责下单、支付回调处理、查单补单的核心闭环(不含退款)。
 */
public interface IPayOrderBizService {

    /**
     * 创建支付订单并发起 JSAPI 下单(不带商品类型,老支付页兼容)
     * 内部委托带商品类型的重载,三个商品字段传 null。
     *
     * @param userId  当前用户ID
     * @param amount  支付金额(单位:分)
     * @param subject 商品描述
     * @return 商户订单号 + 调起支付参数
     */
    CreateOrderVO createOrder(Long userId, Integer amount, String subject);

    /**
     * 创建支付订单并发起 JSAPI 下单(带商品类型,支付成功后按类型分发业务流转)
     * openid 服务端从用户表取,生成商户订单号,落库待支付,返回前端调起支付参数。
     *
     * @param userId      当前用户ID
     * @param amount      支付金额(单位:分)
     * @param subject     商品描述
     * @param productType 订单类型(MEMBERSHIP会员/BEAN_RECHARGE豆子充值,可空=不流转)
     * @param productId   关联业务id(会员等级id/套餐id,可空)
     * @param productName 商品名称快照(可空)
     * @return 商户订单号 + 调起支付参数
     */
    CreateOrderVO createOrder(Long userId, Integer amount, String subject,
                              String productType, Long productId, String productName);

    /**
     * 处理支付成功回调
     * 行锁事务 + 幂等(已支付直接返回)+ 金额一致性校验,通过后置为已支付。
     *
     * @param orderNo       商户订单号
     * @param transactionId 微信支付交易单号
     * @param tradeState    交易状态(仅 SUCCESS 才处理)
     * @param paidFen       实付金额(单位:分)
     */
    void handlePaySuccess(String orderNo, String transactionId, String tradeState, Integer paidFen);

    /**
     * 处理虚拟支付成功。与传统支付共用行锁、金额校验和权益发放，额外保存微信虚拟订单号。
     */
    void handleVirtualPaySuccess(String orderNo, String wxOrderId,
                                 String transactionId, Integer paidFen);

    /**
     * 处理虚拟支付成功，并使用微信侧实际支付完成时间计算会员续期边界。
     * {@code paidTime} 缺失时由实现方回退到当前履约时间。
     */
    void handleVirtualPaySuccess(String orderNo, String wxOrderId,
                                 String transactionId, Integer paidFen,
                                 LocalDateTime paidTime);

    /** 微信虚拟支付全额退款：行锁幂等，并原子撤销已发会员及豆子权益。 */
    void handleVirtualRefund(String orderNo, String refundNo,
                             Integer refundFen, LocalDateTime refundTime);

    /** 微信权威查单确认关闭时，仅把仍待支付的虚拟订单置为关闭。 */
    void handleVirtualClosed(String orderNo);

    /**
     * 查询订单状态(兜底补单)
     * 归属校验本人订单;待支付且 syncWechat=true 时主动同步微信。
     *
     * @param orderNo    商户订单号
     * @param syncWechat 是否主动同步微信
     * @param userId     当前用户ID
     * @return 订单视图
     */
    OrderVO queryOrder(String orderNo, boolean syncWechat, Long userId);

    /**
     * 分页查询我的购买记录(仅本人订单,按下单时间倒序)。
     * 只读列表(虚拟商品不支持退款),出参不含 openId 等敏感字段。
     *
     * @param userId   当前用户ID(取自登录态)
     * @param pageNum  页码(从 1 起)
     * @param pageSize 每页条数(1~50,越界收口)
     * @return 购买记录分页
     */
    IPage<PurchaseRecordVO> listMyOrders(Long userId, int pageNum, int pageSize);
}
