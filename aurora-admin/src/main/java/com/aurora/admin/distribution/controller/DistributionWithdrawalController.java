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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/system/distribution/withdrawal")
@RequiredArgsConstructor
public class DistributionWithdrawalController {

    private final IDistributionQueryService queryService;
    private final IDistributionFinanceService financeService;

    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermi('distribution:withdrawal:list')")
    public Result<IPage<DistributionViews.WithdrawalRow>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String withdrawalNo,
            @RequestParam(required = false) String partnerKeyword,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String methodType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long partnerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime applyStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime applyEnd) {
        return Result.success(queryService.withdrawals(pageNum, pageSize, withdrawalNo, partnerKeyword, phone,
                methodType, status, applyStart, applyEnd, partnerId));
    }

    @GetMapping("/receipt-thumbnails")
    @PreAuthorize("@ss.hasPermi('distribution:withdrawal:sensitive')")
    @Log(title = "查看提现收款码缩略图", isSaveRequestData = false, isSaveResponseData = false)
    public Result<List<DistributionViews.WithdrawalReceiptThumbnail>> receiptThumbnails(
            @RequestParam("ids") List<Long> withdrawalIds) {
        return Result.success(financeService.withdrawalReceiptThumbnails(withdrawalIds));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('distribution:withdrawal:query')")
    public Result<DistributionViews.WithdrawalDetail> detail(@PathVariable("id") Long withdrawalId) {
        return Result.success(queryService.withdrawalDetail(withdrawalId, null));
    }

    @GetMapping("/{id}/sensitive")
    @PreAuthorize("@ss.hasPermi('distribution:withdrawal:sensitive')")
    @Log(title = "查看提现敏感资料", isSaveRequestData = false, isSaveResponseData = false)
    public Result<DistributionViews.WithdrawalSensitive> sensitive(@PathVariable("id") Long withdrawalId) {
        return Result.success(financeService.sensitiveWithdrawal(withdrawalId, null));
    }

    @GetMapping("/{id}/payment-profile")
    @PreAuthorize("@ss.hasPermi('distribution:withdrawal:sensitive')")
    @Log(title = "查看提现打款资料", isSaveRequestData = false, isSaveResponseData = false)
    public Result<DistributionViews.WithdrawalPaymentProfile> paymentProfile(
            @PathVariable("id") Long withdrawalId) {
        return Result.success(financeService.withdrawalPaymentProfile(withdrawalId));
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("@ss.hasPermi('distribution:withdrawal:audit')")
    @Log(title = "驳回分销提现", businessType = BusinessType.UPDATE)
    public Result<Void> reject(@PathVariable("id") Long withdrawalId,
            @Valid @RequestBody DistributionDtos.WithdrawalReject request) {
        financeService.rejectWithdrawal(withdrawalId, request, SecurityUtils.getUsername());
        return Result.success();
    }

    @PutMapping(value = "/{id}/paid", consumes = "multipart/form-data")
    @PreAuthorize("@ss.hasPermi('distribution:withdrawal:pay')")
    @Log(title = "确认分销提现打款", businessType = BusinessType.UPDATE,
            isSaveRequestData = false, isSaveResponseData = false)
    public Result<Void> paid(@PathVariable("id") Long withdrawalId,
            @RequestParam(value = "paymentReference", required = false) String paymentReference,
            @RequestPart(value = "proof", required = false) MultipartFile proofImage) {
        financeService.markWithdrawalPaid(withdrawalId, paymentReference, proofImage, SecurityUtils.getUsername());
        return Result.success();
    }
}
