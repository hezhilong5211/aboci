package com.aurora.admin.distribution.service;

import com.aurora.admin.distribution.vo.DistributionViews;
import com.baomidou.mybatisplus.core.metadata.IPage;

public interface IDistributionMobileService {
    DistributionViews.Me me(Long userId);

    DistributionViews.Dashboard dashboard(Long userId);

    DistributionViews.Qrcode qrcode(Long userId);

    IPage<DistributionViews.PromotionUser> users(Long userId, int pageNum, int pageSize);

    IPage<DistributionViews.CommissionRow> commissions(Long userId, int pageNum, int pageSize);

    IPage<DistributionViews.WithdrawalRow> withdrawals(Long userId, int pageNum, int pageSize);

    DistributionViews.WithdrawalDetail withdrawalDetail(Long userId, Long withdrawalId);

    DistributionViews.WithdrawalSensitive withdrawalSensitive(Long userId, Long withdrawalId);
}
