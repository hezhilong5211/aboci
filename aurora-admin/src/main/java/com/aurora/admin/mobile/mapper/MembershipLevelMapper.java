package com.aurora.admin.mobile.mapper;

import com.aurora.admin.mobile.domain.MembershipLevel;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会员等级配置 Mapper
 */
@Mapper
public interface MembershipLevelMapper extends BaseMapper<MembershipLevel> {
}
