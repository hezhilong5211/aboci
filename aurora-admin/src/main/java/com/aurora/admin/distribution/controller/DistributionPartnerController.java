package com.aurora.admin.distribution.controller;

import com.aurora.admin.distribution.dto.DistributionDtos;
import com.aurora.admin.distribution.service.IDistributionPartnerService;
import com.aurora.admin.distribution.service.IDistributionQrcodeService;
import com.aurora.admin.distribution.service.IDistributionQueryService;
import com.aurora.admin.distribution.vo.DistributionViews;
import com.aurora.common.core.annotation.Log;
import com.aurora.common.core.domain.Result;
import com.aurora.common.core.enums.BusinessType;
import com.aurora.framework.utils.SecurityUtils;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/system/distribution/partner")
@RequiredArgsConstructor
public class DistributionPartnerController {

    private final IDistributionPartnerService partnerService;
    private final IDistributionQueryService queryService;
    private final IDistributionQrcodeService qrcodeService;

    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermi('distribution:partner:list')")
    public Result<IPage<DistributionViews.PartnerRow>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String nickName,
            @RequestParam(required = false) String userNo,
            @RequestParam(required = false) String partnerNo,
            @RequestParam(required = false) String status) {
        return Result.success(queryService.partners(pageNum, pageSize, phone, nickName, userNo, partnerNo, status));
    }

    @GetMapping("/candidates")
    @PreAuthorize("@ss.hasPermi('distribution:partner:list')")
    public Result<IPage<DistributionViews.PartnerCandidate>> candidates(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword) {
        return Result.success(queryService.partnerCandidates(pageNum, pageSize, keyword));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('distribution:partner:query')")
    public Result<DistributionViews.PartnerDetail> detail(@PathVariable("id") Long partnerId) {
        return Result.success(queryService.partnerDetail(partnerId));
    }

    @GetMapping("/{id}/users")
    @PreAuthorize("@ss.hasPermi('distribution:partner:query')")
    public Result<IPage<DistributionViews.PromotionUser>> users(@PathVariable("id") Long partnerId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        partnerService.requireById(partnerId);
        return Result.success(queryService.promotionUsers(partnerId, pageNum, pageSize));
    }

    @GetMapping("/{id}/orders")
    @PreAuthorize("@ss.hasPermi('distribution:partner:query')")
    public Result<IPage<DistributionViews.PromotionOrder>> orders(@PathVariable("id") Long partnerId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        partnerService.requireById(partnerId);
        return Result.success(queryService.promotionOrders(partnerId, pageNum, pageSize));
    }

    @GetMapping("/{id}/qrcode")
    @PreAuthorize("@ss.hasPermi('distribution:partner:query')")
    public Result<DistributionViews.Qrcode> qrcode(@PathVariable("id") Long partnerId) {
        return Result.success(qrcodeService.getQrcode(partnerId));
    }

    @PostMapping
    @PreAuthorize("@ss.hasPermi('distribution:partner:add')")
    @Log(title = "新增分销商", businessType = BusinessType.INSERT)
    public Result<Long> add(@Valid @RequestBody DistributionDtos.PartnerAdd request) {
        return Result.success(partnerService.addPartner(request, SecurityUtils.getUsername()));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("@ss.hasPermi('distribution:partner:edit')")
    @Log(title = "修改分销商状态", businessType = BusinessType.UPDATE)
    public Result<Void> updateStatus(@PathVariable("id") Long partnerId,
            @Valid @RequestBody DistributionDtos.PartnerStatus request) {
        partnerService.updateStatus(partnerId, request, SecurityUtils.getUsername());
        return Result.success();
    }
}
