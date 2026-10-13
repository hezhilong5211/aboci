package com.aurora.admin.distribution.service;

import com.aurora.admin.distribution.domain.DistributionConfig;
import com.aurora.admin.distribution.domain.DistributionPartner;
import com.aurora.admin.distribution.dto.DistributionDtos;
import com.aurora.admin.distribution.vo.DistributionViews;

public interface IDistributionPartnerService {
    Long addPartner(DistributionDtos.PartnerAdd request, String operator);

    void updateStatus(Long partnerId, DistributionDtos.PartnerStatus request, String operator);

    DistributionConfig getConfig();

    DistributionViews.ConfigView getConfigView();

    void updateConfig(DistributionDtos.ConfigUpdate request, String operator);

    DistributionPartner getByUserId(Long userId);

    DistributionPartner requireByUserId(Long userId);

    DistributionPartner requireById(Long partnerId);
}
