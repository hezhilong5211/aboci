package com.aurora.admin.mobile.service;

import com.aurora.admin.mobile.domain.SysWxConfig;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 微信应用配置 Service 接口
 */
public interface ISysWxConfigService extends IService<SysWxConfig> {

    /**
     * 按应用类型查询启用中的配置(is_active=1),供 WxServiceFactory 构建客户端用;无则返回 null
     *
     * @param appType 应用类型(mini_app/pay)
     */
    SysWxConfig getActiveByType(String appType);
}
