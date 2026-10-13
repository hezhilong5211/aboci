package com.aurora.common.core.mapper;

import com.aurora.common.core.domain.entity.SysOperLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 操作日志 数据层
 */
@Mapper
public interface SysOperLogMapper extends BaseMapper<SysOperLog> {
}
