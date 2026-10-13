package com.aurora.admin.mobile.service.impl;

import com.aurora.admin.mobile.domain.SysWxConfig;
import com.aurora.admin.mobile.mapper.SysWxConfigMapper;
import com.aurora.admin.mobile.service.ISysWxConfigService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 微信应用配置 Service 实现类
 */
@Service
public class SysWxConfigServiceImpl extends ServiceImpl<SysWxConfigMapper, SysWxConfig>
        implements ISysWxConfigService {

    /**
     * 按应用类型查询启用中的配置(is_active=1),供 WxServiceFactory 构建客户端用;无则返回 null
     */
    @Override
    public SysWxConfig getActiveByType(String appType) {
        LambdaQueryWrapper<SysWxConfig> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysWxConfig::getAppType, appType)
                .eq(SysWxConfig::getIsActive, 1)
                .last("LIMIT 1");
        return getOne(queryWrapper);
    }
}
