package com.aurora.common.core.service;

import com.aurora.common.core.domain.entity.SysConfig;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 参数配置业务层
 */
public interface ISysConfigService extends IService<SysConfig> {
    /**
     * 根据键名查询参数配置信息
     * 
     * @param configKey 参数键名
     * @return 参数键值
     */
    public String selectConfigByKey(String configKey);
}
