package com.aurora.admin.controller.mobile;

import com.aurora.admin.mobile.domain.SysMobileTabbar;
import com.aurora.admin.mobile.service.ISysMobileTabbarService;
import com.aurora.common.core.annotation.Log;
import com.aurora.common.core.domain.Result;
import com.aurora.common.core.enums.BusinessType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 移动端底部导航 管理后台控制器
 * 仅 list + edit:后台可改名称/双态图标/显隐/排序;tab_key、page_path 前端固定、只读不可改。
 */
@Tag(name = "移动端底部导航", description = "移动端底部导航后台配置")
@RestController
@RequestMapping("/system/mobile/tabbar")
@RequiredArgsConstructor
public class SysMobileTabbarController {

    private final ISysMobileTabbarService tabbarService;

    /**
     * 查询全部 Tab(按 sort 升序)
     */
    @Operation(summary = "查询全部 Tab", description = "返回全部底部导航配置,按 sort 升序")
    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermi('mobile:tabbar:list')")
    public Result<List<SysMobileTabbar>> list() {
        return Result.success(tabbarService.listAllOrderBySort());
    }

    /**
     * 编辑 Tab(仅 tab_name/icon_normal/icon_active/visible/sort 可改)
     */
    @Operation(summary = "编辑 Tab", description = "按主键更新名称/双态图标/显隐/排序;tab_key、page_path 只读不可改")
    @PutMapping
    @PreAuthorize("@ss.hasPermi('mobile:tabbar:edit')")
    @Log(title = "移动端底部导航", businessType = BusinessType.UPDATE)
    public Result<Void> edit(@RequestBody SysMobileTabbar body) {
        if (body.getTabId() == null) {
            return Result.error("tabId 不能为空");
        }
        boolean ok = tabbarService.editEditable(body);
        return ok ? Result.success() : Result.error("Tab 不存在或未变更");
    }
}
