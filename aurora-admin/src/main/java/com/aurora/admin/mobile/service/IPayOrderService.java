package com.aurora.admin.mobile.service;

import com.aurora.admin.mobile.domain.PayOrder;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

import java.time.LocalDateTime;

/**
 * 支付订单 Service 接口
 */
public interface IPayOrderService extends IService<PayOrder> {

    /**
     * 后台订单分页查询(按创建时间倒序),供订单管理列表用。
     *
     * @param status      状态(0待支付/1已支付/2已退款/3已关闭/4已支付待退款,可空)
     * @param productType 订单类型(MEMBERSHIP/BEAN_RECHARGE,可空)
     * @param keyword     关键词(匹配订单号或用户ID,可空)
     * @param beginTime   创建时间起(含,可空)
     * @param endTime     创建时间止(含,可空)
     * @param pageNum     页码
     * @param pageSize    每页条数
     * @return 订单分页
     */
    IPage<PayOrder> pageOrders(Integer status, String productType, String keyword,
                               LocalDateTime beginTime, LocalDateTime endTime,
                               Integer pageNum, Integer pageSize);
}
