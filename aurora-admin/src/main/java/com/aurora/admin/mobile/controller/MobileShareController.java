package com.aurora.admin.mobile.controller;

import com.aurora.common.core.domain.Result;
import com.aurora.common.core.service.ISysConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 移动端分享配置(免鉴权:分享随 App 启动读取,游客也可分享;
 * 放行见 SecurityConfig mobileSecurityFilterChain,与 /api/mobile/tabbar 同款)。
 * 配置存 sys_config(mobile.share.title / mobile.share.image),后台「系统设置-分享设置」卡片维护。
 */
@RestController
@RequestMapping("/api/mobile/share")
@RequiredArgsConstructor
public class MobileShareController {

    private final ISysConfigService sysConfigService;

    /**
     * 分享配置:标题 + 卡片图 /profile URL(空=前端不传 imageUrl,微信自动用页面截图)。
     */
    @GetMapping
    public Result<Map<String, Object>> shareConfig() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("title", sysConfigService.selectConfigByKey("mobile.share.title"));
        data.put("imageUrl", sysConfigService.selectConfigByKey("mobile.share.image"));
        return Result.success(data);
    }
}
