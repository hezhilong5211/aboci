package com.aurora.admin.distribution.mapper;

import com.aurora.admin.distribution.domain.DistributionVisit;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DistributionVisitMapper extends BaseMapper<DistributionVisit> {
    @Select("SELECT * FROM distribution_visit WHERE event_id = #{eventId} LIMIT 1")
    DistributionVisit selectByEventId(@Param("eventId") String eventId);

    @Select("SELECT * FROM distribution_visit WHERE event_id = #{eventId} FOR UPDATE")
    DistributionVisit selectByEventIdForUpdate(@Param("eventId") String eventId);
}
