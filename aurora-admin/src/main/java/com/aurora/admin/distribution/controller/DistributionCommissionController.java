package com.aurora.admin.distribution.controller;

import com.aurora.admin.distribution.dto.DistributionDtos;
import com.aurora.admin.distribution.service.IDistributionFinanceService;
import com.aurora.admin.distribution.service.IDistributionQueryService;
import com.aurora.admin.distribution.vo.DistributionViews;
import com.aurora.common.core.annotation.Log;
import com.aurora.common.core.domain.Result;
import com.aurora.common.core.enums.BusinessType;
import com.aurora.framework.utils.SecurityUtils;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/system/distribution/commission")
@RequiredArgsConstructor
public class DistributionCommissionController {

    private final IDistributionQueryService queryService;
    private final IDistributionFinanceService financeService;

    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermi('distribution:commission:list')")
    public Result<IPage<DistributionViews.CommissionRow>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String partnerKeyword,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String productType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long partnerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime payStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime payEnd) {
        return Result.success(queryService.commissions(pageNum, pageSize, partnerKeyword, phone, orderNo,
                productType, status, payStart, payEnd, partnerId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('distribution:commission:query')")
    public Result<DistributionViews.CommissionDetail> detail(@PathVariable("id") Long commissionId) {
        return Result.success(queryService.commissionDetail(commissionId));
    }

    @PutMapping("/{id}/adjust")
    @PreAuthorize("@ss.hasPermi('distribution:commission:adjust')")
    @Log(title = "调整分销佣金", businessType = BusinessType.UPDATE)
    public Result<Void> adjust(@PathVariable("id") Long commissionId,
            @Valid @RequestBody DistributionDtos.CommissionAdjust request) {
        financeService.adjustCommission(commissionId, request, SecurityUtils.getUsername());
        return Result.success();
    }
}
