package com.aurora.admin.distribution.mapper;

import com.aurora.admin.distribution.domain.DistributionWithdrawal;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DistributionWithdrawalMapper extends BaseMapper<DistributionWithdrawal> {
    @Select("SELECT * FROM distribution_withdrawal WHERE withdrawal_id = #{withdrawalId} FOR UPDATE")
    DistributionWithdrawal selectByIdForUpdate(@Param("withdrawalId") Long withdrawalId);
}
