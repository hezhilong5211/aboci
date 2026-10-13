package com.aurora.admin.controller.system;

import com.aurora.common.core.domain.Result;
import com.aurora.common.core.domain.entity.SysIcon;
import com.aurora.common.core.service.ISysIconService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 自定义图标控制器
 */
@Tag(name = "图标管理", description = "自定义图标的上传、删除、查询等接口")
@RestController
@RequestMapping("/system/icon")
public class SysIconController {

    private final ISysIconService iconService;

    public SysIconController(ISysIconService iconService) {
        this.iconService = iconService;
    }

    /**
     * 上传图标
     */
    @Operation(summary = "上传图标", description = "上传PNG或SVG格式的自定义图标")
    @PostMapping("/upload")
    @PreAuthorize("@ss.hasPermi('system:icon:add')")
    public Result<SysIcon> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "iconName", required = false) String iconName) {
        try {
            SysIcon icon = iconService.uploadIcon(file, iconName);
            return Result.success(icon);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取所有自定义图标
     */
    @Operation(summary = "获取图标列表", description = "获取所有有效的自定义图标")
    // 图标列表供布局/菜单 IconRenderer 渲染,所有登录用户均需;放开管理权限,仅需认证即可读
    @GetMapping("/list")
    public Result<List<SysIcon>> list() {
        List<SysIcon> icons = iconService.listAllIcons();
        return Result.success(icons);
    }

    /**
     * 删除图标
     */
    @Operation(summary = "删除图标", description = "根据图标ID删除自定义图标")
    @DeleteMapping("/{iconId}")
    @PreAuthorize("@ss.hasPermi('system:icon:remove')")
    public Result<Void> remove(@PathVariable Long iconId) {
        try {
            boolean success = iconService.deleteIcon(iconId);
            return success ? Result.success() : Result.error("删除失败");
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取下一个自动生成的图标名称（用于预览）
     */
    @Operation(summary = "获取下一个图标名称", description = "获取自动生成的下一个图标名称，用于前端预览")
    @GetMapping("/nextName")
    @PreAuthorize("@ss.hasPermi('system:icon:list')")
    public Result<String> getNextName() {
        String nextName = iconService.generateNextIconName();
        return Result.success(nextName);
    }
}
