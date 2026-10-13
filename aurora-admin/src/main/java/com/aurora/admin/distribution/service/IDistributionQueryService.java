package com.aurora.admin.distribution.service;

import com.aurora.admin.distribution.vo.DistributionViews;
import com.baomidou.mybatisplus.core.metadata.IPage;

import java.time.LocalDateTime;

public interface IDistributionQueryService {
    DistributionViews.Overview overview();

    IPage<DistributionViews.PartnerCandidate> partnerCandidates(int pageNum, int pageSize, String keyword);

    IPage<DistributionViews.PartnerRow> partners(int pageNum, int pageSize, String phone, String nickName,
            String userNo, String partnerNo, String status);

    DistributionViews.PartnerDetail partnerDetail(Long partnerId);

    IPage<DistributionViews.PromotionUser> promotionUsers(Long partnerId, int pageNum, int pageSize);

    IPage<DistributionViews.PromotionOrder> promotionOrders(Long partnerId, int pageNum, int pageSize);

    IPage<DistributionViews.CommissionRow> commissions(int pageNum, int pageSize, String partnerKeyword,
            String phone, String orderNo, String productType, String status,
            LocalDateTime payStart, LocalDateTime payEnd, Long partnerId);

    DistributionViews.CommissionDetail commissionDetail(Long commissionId);

    IPage<DistributionViews.WithdrawalRow> withdrawals(int pageNum, int pageSize, String withdrawalNo,
            String partnerKeyword, String phone, String methodType, String status,
            LocalDateTime applyStart, LocalDateTime applyEnd, Long partnerId);

    DistributionViews.WithdrawalDetail withdrawalDetail(Long withdrawalId, Long ownerPartnerId);

    DistributionViews.Dashboard dashboard(Long partnerId);
}
