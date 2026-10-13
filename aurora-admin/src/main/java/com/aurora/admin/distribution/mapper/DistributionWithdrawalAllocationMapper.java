package com.aurora.admin.distribution.mapper;

import com.aurora.admin.distribution.domain.DistributionWithdrawalAllocation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DistributionWithdrawalAllocationMapper extends BaseMapper<DistributionWithdrawalAllocation> {
    @Select("SELECT * FROM distribution_withdrawal_allocation "
            + "WHERE withdrawal_id = #{withdrawalId} ORDER BY allocation_id")
    List<DistributionWithdrawalAllocation> selectByWithdrawal(@Param("withdrawalId") Long withdrawalId);

    @Select("SELECT * FROM distribution_withdrawal_allocation "
            + "WHERE withdrawal_id = #{withdrawalId} ORDER BY allocation_id FOR UPDATE")
    List<DistributionWithdrawalAllocation> selectByWithdrawalForUpdate(@Param("withdrawalId") Long withdrawalId);

    @Select("SELECT * FROM distribution_withdrawal_allocation "
            + "WHERE commission_id = #{commissionId} AND status IN ('LOCKED', 'PAID') "
            + "ORDER BY allocation_id")
    List<DistributionWithdrawalAllocation> selectActiveByCommission(@Param("commissionId") Long commissionId);

    @Select("SELECT * FROM distribution_withdrawal_allocation "
            + "WHERE commission_id = #{commissionId} AND status IN ('LOCKED', 'PAID') "
            + "ORDER BY allocation_id FOR UPDATE")
    List<DistributionWithdrawalAllocation> selectActiveByCommissionForUpdate(
            @Param("commissionId") Long commissionId);
}
