package com.aurora.admin.distribution.controller;

import com.aurora.admin.distribution.dto.DistributionDtos;
import com.aurora.admin.distribution.service.IDistributionPartnerService;
import com.aurora.admin.distribution.vo.DistributionViews;
import com.aurora.common.core.annotation.Log;
import com.aurora.common.core.domain.Result;
import com.aurora.common.core.enums.BusinessType;
import com.aurora.framework.utils.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/system/distribution/config")
@RequiredArgsConstructor
public class DistributionConfigController {

    private final IDistributionPartnerService partnerService;

    @GetMapping
    @PreAuthorize("@ss.hasPermi('distribution:config:query')")
    public Result<DistributionViews.ConfigView> get() {
        return Result.success(partnerService.getConfigView());
    }

    @PutMapping
    @PreAuthorize("@ss.hasPermi('distribution:config:edit')")
    @Log(title = "修改分销设置", businessType = BusinessType.UPDATE)
    public Result<Void> update(@Valid @RequestBody DistributionDtos.ConfigUpdate request) {
        partnerService.updateConfig(request, SecurityUtils.getUsername());
        return Result.success();
    }
}
