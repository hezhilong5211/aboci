package com.aurora.admin.distribution.controller;

import com.aurora.admin.distribution.dto.DistributionDtos;
import com.aurora.admin.distribution.service.IDistributionAttributionService;
import com.aurora.admin.distribution.service.IDistributionFinanceService;
import com.aurora.admin.distribution.service.IDistributionMobileService;
import com.aurora.admin.distribution.vo.DistributionViews;
import com.aurora.common.core.domain.Result;
import com.aurora.framework.annotation.RateLimiter;
import com.aurora.framework.mobile.MobileJwtFilter;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/mobile/distribution")
@RequiredArgsConstructor
public class MobileDistributionController {

    private final IDistributionAttributionService attributionService;
    private final IDistributionMobileService mobileService;
    private final IDistributionFinanceService financeService;

    @PostMapping("/visit")
    @RateLimiter(key = "mobile:distribution:visit", time = 60, count = 30,
            limitType = RateLimiter.LimitType.IP, message = "访问过于频繁，请稍后再试")
    public Result<Void> visit(@Valid @RequestBody DistributionDtos.Visit request) {
        attributionService.recordVisit(request);
        return Result.success();
    }

    @PostMapping("/attribution/bind")
    public Result<DistributionViews.BindResult> bind(
            @RequestAttribute(MobileJwtFilter.MOBILE_USER_ID_ATTR) Long userId,
            @Valid @RequestBody DistributionDtos.Bind request) {
        return Result.success(attributionService.bind(userId, request));
    }

    @GetMapping("/me")
    public Result<DistributionViews.Me> me(
            @RequestAttribute(MobileJwtFilter.MOBILE_USER_ID_ATTR) Long userId) {
        return Result.success(mobileService.me(userId));
    }

    @GetMapping("/dashboard")
    public Result<DistributionViews.Dashboard> dashboard(
            @RequestAttribute(MobileJwtFilter.MOBILE_USER_ID_ATTR) Long userId) {
        return Result.success(mobileService.dashboard(userId));
    }

    @GetMapping("/qrcode")
    public Result<DistributionViews.Qrcode> qrcode(
            @RequestAttribute(MobileJwtFilter.MOBILE_USER_ID_ATTR) Long userId) {
        return Result.success(mobileService.qrcode(userId));
    }

    @GetMapping("/users")
    public Result<IPage<DistributionViews.PromotionUser>> users(
            @RequestAttribute(MobileJwtFilter.MOBILE_USER_ID_ATTR) Long userId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(mobileService.users(userId, pageNum, pageSize));
    }

    @GetMapping("/commissions")
    public Result<IPage<DistributionViews.CommissionRow>> commissions(
            @RequestAttribute(MobileJwtFilter.MOBILE_USER_ID_ATTR) Long userId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(mobileService.commissions(userId, pageNum, pageSize));
    }

    @GetMapping("/withdrawals")
    public Result<IPage<DistributionViews.WithdrawalRow>> withdrawals(
            @RequestAttribute(MobileJwtFilter.MOBILE_USER_ID_ATTR) Long userId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(mobileService.withdrawals(userId, pageNum, pageSize));
    }

    @PostMapping(value = "/withdrawals", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Result<Long> applyBankWithdrawal(
            @RequestAttribute(MobileJwtFilter.MOBILE_USER_ID_ATTR) Long userId,
            @Valid @RequestBody DistributionDtos.WithdrawalApply request) {
        return Result.success(financeService.applyWithdrawal(userId, request, null));
    }

    @PostMapping(value = "/withdrawals", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Long> applyQrWithdrawal(
            @RequestAttribute(MobileJwtFilter.MOBILE_USER_ID_ATTR) Long userId,
            @Valid @ModelAttribute DistributionDtos.WithdrawalApply request,
            @RequestPart("receipt") MultipartFile receiptImage) {
        return Result.success(financeService.applyWithdrawal(userId, request, receiptImage));
    }

    @GetMapping("/withdrawals/{id}")
    public Result<DistributionViews.WithdrawalDetail> withdrawalDetail(
            @RequestAttribute(MobileJwtFilter.MOBILE_USER_ID_ATTR) Long userId,
            @PathVariable("id") Long withdrawalId) {
        return Result.success(mobileService.withdrawalDetail(userId, withdrawalId));
    }

    @GetMapping("/withdrawals/{id}/sensitive")
    public Result<DistributionViews.WithdrawalSensitive> withdrawalSensitive(
            @RequestAttribute(MobileJwtFilter.MOBILE_USER_ID_ATTR) Long userId,
            @PathVariable("id") Long withdrawalId) {
        return Result.success(mobileService.withdrawalSensitive(userId, withdrawalId));
    }
}
