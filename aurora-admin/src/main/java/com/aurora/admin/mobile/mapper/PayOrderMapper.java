package com.aurora.admin.mobile.mapper;

import com.aurora.admin.mobile.domain.PayOrder;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 支付订单 Mapper
 */
@Mapper
public interface PayOrderMapper extends BaseMapper<PayOrder> {

    /**
     * 按商户订单号加行锁查询(FOR UPDATE),配合 @Transactional 防回调并发竞态
     *
     * @param orderNo 商户订单号
     * @return 订单(不存在返回 null)
     */
    @Select("SELECT * FROM pay_order WHERE order_no = #{orderNo} FOR UPDATE")
    PayOrder findByOrderNoForUpdate(@Param("orderNo") String orderNo);
}
