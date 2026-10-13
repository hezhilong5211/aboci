package com.aurora.admin.distribution.service.impl;

import com.aurora.admin.distribution.domain.DistributionConfig;
import com.aurora.admin.distribution.domain.DistributionPartner;
import com.aurora.admin.distribution.service.IDistributionFinanceService;
import com.aurora.admin.distribution.service.IDistributionMobileService;
import com.aurora.admin.distribution.service.IDistributionPartnerService;
import com.aurora.admin.distribution.service.IDistributionQrcodeService;
import com.aurora.admin.distribution.service.IDistributionQueryService;
import com.aurora.admin.distribution.support.DistributionConstants;
import com.aurora.admin.distribution.vo.DistributionViews;
import com.aurora.common.core.exception.ServiceException;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DistributionMobileServiceImpl implements IDistributionMobileService {

    private final IDistributionPartnerService partnerService;
    private final IDistributionQueryService queryService;
    private final IDistributionQrcodeService qrcodeService;
    private final IDistributionFinanceService financeService;

    @Override
    public DistributionViews.Me me(Long userId) {
        DistributionConfig config = partnerService.getConfig();
        DistributionPartner partner = partnerService.getByUserId(userId);
        DistributionViews.Me result = new DistributionViews.Me();
        result.setPartner(partner != null);
        result.setEntryVisible(partner != null && config.getEnabled() == 1);
        result.setPromotionEnabled(partner != null && config.getEnabled() == 1
                && DistributionConstants.PARTNER_ACTIVE.equals(partner.getStatus()));
        if (partner != null) {
            result.setPartnerId(partner.getPartnerId());
            result.setPartnerNo(partner.getPartnerNo());
            result.setStatus(partner.getStatus());
        }
        return result;
    }

    @Override
    public DistributionViews.Dashboard dashboard(Long userId) {
        DistributionPartner partner = requireVisiblePartner(userId);
        return queryService.dashboard(partner.getPartnerId());
    }

    @Override
    public DistributionViews.Qrcode qrcode(Long userId) {
        DistributionPartner partner = requireVisiblePartner(userId);
        return qrcodeService.getQrcode(partner.getPartnerId());
    }

    @Override
    public IPage<DistributionViews.PromotionUser> users(Long userId, int pageNum, int pageSize) {
        DistributionPartner partner = requireVisiblePartner(userId);
        return queryService.promotionUsers(partner.getPartnerId(), pageNum, pageSize);
    }

    @Override
    public IPage<DistributionViews.CommissionRow> commissions(Long userId, int pageNum, int pageSize) {
        DistributionPartner partner = requireVisiblePartner(userId);
        return queryService.commissions(pageNum, pageSize, null, null, null, null, null,
                null, null, partner.getPartnerId());
    }

    @Override
    public IPage<DistributionViews.WithdrawalRow> withdrawals(Long userId, int pageNum, int pageSize) {
        DistributionPartner partner = requireVisiblePartner(userId);
        return queryService.withdrawals(pageNum, pageSize, null, null, null, null, null,
                null, null, partner.getPartnerId());
    }

    @Override
    public DistributionViews.WithdrawalDetail withdrawalDetail(Long userId, Long withdrawalId) {
        DistributionPartner partner = requireVisiblePartner(userId);
        return queryService.withdrawalDetail(withdrawalId, partner.getPartnerId());
    }

    @Override
    public DistributionViews.WithdrawalSensitive withdrawalSensitive(Long userId, Long withdrawalId) {
        DistributionPartner partner = requireVisiblePartner(userId);
        return financeService.sensitiveWithdrawal(withdrawalId, partner.getPartnerId());
    }

    private DistributionPartner requireVisiblePartner(Long userId) {
        DistributionConfig config = partnerService.getConfig();
        if (config.getEnabled() != 1) {
            throw new ServiceException("分销功能暂未开启");
        }
        return partnerService.requireByUserId(userId);
    }
}
