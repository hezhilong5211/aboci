package com.aurora.admin.distribution.mapper;

import com.aurora.admin.distribution.domain.DistributionConfig;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DistributionConfigMapper extends BaseMapper<DistributionConfig> {
    @Select("SELECT * FROM distribution_config WHERE config_id = 1 FOR UPDATE")
    DistributionConfig selectForUpdate();
}
