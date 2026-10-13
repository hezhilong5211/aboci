package com.aurora.common.core.service;

import com.aurora.common.core.domain.entity.SysOperLog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 操作日志 服务层接口
 */
public interface ISysOperLogService extends IService<SysOperLog> {

    /**
     * 清空操作日志
     */
    void cleanOperLog();
}
