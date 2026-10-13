package com.aurora.admin.mobile.mapper;

import com.aurora.admin.mobile.domain.MobileUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 移动端用户 Mapper
 */
@Mapper
public interface MobileUserMapper extends BaseMapper<MobileUser> {
}
