package com.aurora.admin.mobile.controller;

import com.aurora.admin.mobile.service.ISysMobileTabbarService;
import com.aurora.admin.mobile.vo.TabbarConfigVO;
import com.aurora.common.core.domain.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 移动端底部导航取数控制器
 * 免鉴权(App 启动早于登录,放行见 SecurityConfig.mobileSecurityFilterChain),
 * 返回全部 Tab(显隐交前端过滤),字段名对齐 hagent normalizeTabbarConfig。
 */
@Tag(name = "移动端底部导航", description = "小程序获取底部导航配置")
@RestController
@RequestMapping("/api/mobile/tabbar")
@RequiredArgsConstructor
public class MobileTabbarController {

    private final ISysMobileTabbarService tabbarService;

    /**
     * 获取底部导航配置(免鉴权)
     */
    @Operation(summary = "获取底部导航配置")
    @GetMapping
    public Result<TabbarConfigVO> getConfig() {
        return Result.success(TabbarConfigVO.from(tabbarService.listAllOrderBySort()));
    }
}
