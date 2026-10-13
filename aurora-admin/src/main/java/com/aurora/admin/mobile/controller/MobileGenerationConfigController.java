package com.aurora.admin.mobile.controller;

import com.aurora.admin.mobile.dto.GenerationConfigDTO;
import com.aurora.admin.mobile.service.IGenerationConfigService;
import com.aurora.common.core.domain.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 小程序读取 AI 生成页面与下载水印状态。
 */
@RestController
@RequestMapping("/api/mobile/generation/config")
@RequiredArgsConstructor
public class MobileGenerationConfigController {

    private final IGenerationConfigService generationConfigService;

    @GetMapping
    public Result<GenerationConfigDTO> getConfig() {
        return Result.success(generationConfigService.getConfig());
    }
}
