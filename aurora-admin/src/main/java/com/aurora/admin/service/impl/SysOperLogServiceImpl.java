package com.aurora.admin.service.impl;

import com.aurora.common.core.service.ISysOperLogService;
import com.aurora.common.core.domain.entity.SysOperLog;
import com.aurora.common.core.mapper.SysOperLogMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 操作日志 服务层实现
 */
@Service
public class SysOperLogServiceImpl extends ServiceImpl<SysOperLogMapper, SysOperLog> implements ISysOperLogService {

    @Override
    public void cleanOperLog() {
        // 清空操作日志表
        baseMapper.delete(null);
    }
}
