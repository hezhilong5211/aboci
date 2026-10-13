package com.aurora.admin.controller.mobile;

import com.aurora.admin.mobile.dto.GenerationConfigDTO;
import com.aurora.admin.mobile.service.IGenerationConfigService;
import com.aurora.common.core.annotation.Log;
import com.aurora.common.core.domain.Result;
import com.aurora.common.core.enums.BusinessType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统设置中的 AI 生成配置管理。
 */
@RestController
@RequestMapping("/system/generation-config")
@RequiredArgsConstructor
public class GenerationConfigController {

    private final IGenerationConfigService generationConfigService;

    @GetMapping
    @PreAuthorize("@ss.hasAnyPermi('system:generationconfig:list', 'system:generationconfig:edit')")
    public Result<GenerationConfigDTO> getConfig() {
        return Result.success(generationConfigService.getConfig());
    }

    @PutMapping
    @PreAuthorize("@ss.hasPermi('system:generationconfig:edit')")
    @Log(title = "AI 生成配置", businessType = BusinessType.UPDATE)
    public Result<Void> updateConfig(@Valid @RequestBody GenerationConfigDTO config) {
        generationConfigService.updateConfig(config);
        return Result.success();
    }
}
