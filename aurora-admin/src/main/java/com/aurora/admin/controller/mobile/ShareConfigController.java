package com.aurora.admin.controller.mobile;

import com.aurora.admin.mobile.dto.ShareConfigDTO;
import com.aurora.admin.mobile.service.IShareConfigService;
import com.aurora.common.core.annotation.Log;
import com.aurora.common.core.domain.Result;
import com.aurora.common.core.enums.BusinessType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 小程序分享配置管理。
 *
 * 使用独立接口和固定配置键，避免向系统设置角色开放通用参数配置权限。
 */
@Tag(name = "小程序分享配置", description = "维护分享标题与分享卡片图")
@RestController
@RequestMapping("/system/share-config")
@RequiredArgsConstructor
public class ShareConfigController {

    private final IShareConfigService shareConfigService;

    @Operation(summary = "查询分享配置")
    @GetMapping
    @PreAuthorize("@ss.hasAnyPermi('system:shareconfig:list', 'system:shareconfig:edit')")
    public Result<ShareConfigDTO> getShareConfig() {
        return Result.success(shareConfigService.getShareConfig());
    }

    @Operation(summary = "更新分享配置")
    @PutMapping
    @PreAuthorize("@ss.hasPermi('system:shareconfig:edit')")
    @Log(title = "小程序分享配置", businessType = BusinessType.UPDATE)
    public Result<Void> updateShareConfig(@Valid @RequestBody ShareConfigDTO config) {
        shareConfigService.updateShareConfig(config);
        return Result.success();
    }
}
