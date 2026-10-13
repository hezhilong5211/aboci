package com.aurora.admin.distribution.mapper;

import com.aurora.admin.distribution.domain.DistributionRelation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DistributionRelationMapper extends BaseMapper<DistributionRelation> {
    @Select("SELECT * FROM distribution_relation WHERE referred_user_id = #{userId} LIMIT 1")
    DistributionRelation selectByReferredUserId(@Param("userId") Long userId);
}
