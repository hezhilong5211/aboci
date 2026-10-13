package com.aurora.admin.dashboard.controller;

import com.aurora.admin.dashboard.service.IDashboardService;
import com.aurora.admin.dashboard.vo.DashboardVO;
import com.aurora.common.core.domain.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台首页数据概览
 */
@Tag(name = "后台首页统计")
@RestController
@RequestMapping("/system/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final IDashboardService dashboardService;

    /**
     * 首页数据概览
     * 例外:首页所有登录用户可看(无角色门槛),故用 isAuthenticated() 而非 @ss.hasPermi,
     * 属"使用类"只读端点,不挂菜单权限串。
     */
    @Operation(summary = "首页数据概览")
    @GetMapping("/overview")
    @PreAuthorize("isAuthenticated()")
    public Result<DashboardVO> overview() {
        return Result.success(dashboardService.overview());
    }
}
