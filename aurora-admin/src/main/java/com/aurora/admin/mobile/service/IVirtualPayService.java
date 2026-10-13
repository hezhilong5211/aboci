package com.aurora.admin.mobile.service;

import com.aurora.admin.mobile.dto.VirtualPayCreateDTO;
import com.aurora.admin.mobile.dto.VirtualPayClientFailureDTO;
import com.aurora.admin.mobile.dto.VirtualPayGoodsNotifyDTO;
import com.aurora.admin.mobile.dto.VirtualPayRefundNotifyDTO;
import com.aurora.admin.mobile.vo.OrderVO;
import com.aurora.admin.mobile.vo.VirtualPayCreateVO;

/**
 * 微信小程序虚拟支付（普通道具直购）业务。
 */
public interface IVirtualPayService {

    /** 创建本地订单并生成 requestVirtualPayment 双签名。 */
    VirtualPayCreateVO create(Long userId, VirtualPayCreateDTO dto);

    /**
     * 查询本人虚拟支付订单。
     *
     * @param syncWechat true=必要时调微信查单并补发；false=仅读本地状态，供支付弹窗期间低成本轮询
     */
    OrderVO query(Long userId, String orderNo, boolean syncWechat);

    /** 记录 requestVirtualPayment 客户端失败原始码；仅用于诊断，不改变订单状态。 */
    void reportClientFailure(Long userId, VirtualPayClientFailureDTO dto);

    /** 处理微信 xpay_goods_deliver_notify 推送。 */
    void handleGoodsDeliver(VirtualPayGoodsNotifyDTO notify);

    /** 处理微信 xpay_refund_notify 推送，并在同一事务自动撤销对应会员及豆子权益。 */
    void handleRefund(VirtualPayRefundNotifyDTO notify);
}
