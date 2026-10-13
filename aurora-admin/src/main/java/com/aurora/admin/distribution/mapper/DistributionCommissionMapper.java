package com.aurora.admin.distribution.mapper;

import com.aurora.admin.distribution.domain.DistributionCommission;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DistributionCommissionMapper extends BaseMapper<DistributionCommission> {
    @Select("SELECT * FROM distribution_commission WHERE order_no = #{orderNo} LIMIT 1")
    DistributionCommission selectByOrderNo(@Param("orderNo") String orderNo);

    @Select("SELECT * FROM distribution_commission WHERE order_no = #{orderNo} FOR UPDATE")
    DistributionCommission selectByOrderNoForUpdate(@Param("orderNo") String orderNo);

    @Select("SELECT * FROM distribution_commission "
            + "WHERE partner_id = #{partnerId} AND available_amount > 0 "
            + "ORDER BY pay_time ASC, commission_id ASC FOR UPDATE")
    List<DistributionCommission> selectAvailableForUpdate(@Param("partnerId") Long partnerId);

    @Select("SELECT * FROM distribution_commission WHERE commission_id = #{commissionId} FOR UPDATE")
    DistributionCommission selectByIdForUpdate(@Param("commissionId") Long commissionId);
}
