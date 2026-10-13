package com.aurora.admin.distribution.mapper;

import com.aurora.admin.distribution.domain.DistributionAccount;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DistributionAccountMapper extends BaseMapper<DistributionAccount> {
    @Select("SELECT * FROM distribution_account WHERE partner_id = #{partnerId} FOR UPDATE")
    DistributionAccount selectForUpdate(@Param("partnerId") Long partnerId);
}
