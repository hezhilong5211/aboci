package com.aurora.admin.distribution.controller;

import com.aurora.admin.distribution.service.IDistributionQueryService;
import com.aurora.admin.distribution.vo.DistributionViews;
import com.aurora.common.core.domain.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/system/distribution/overview")
@RequiredArgsConstructor
public class DistributionOverviewController {

    private final IDistributionQueryService queryService;

    @GetMapping
    @PreAuthorize("@ss.hasPermi('distribution:overview:list')")
    public Result<DistributionViews.Overview> overview() {
        return Result.success(queryService.overview());
    }
}
